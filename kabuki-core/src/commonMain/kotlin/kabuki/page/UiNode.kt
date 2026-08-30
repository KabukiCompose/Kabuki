package kabuki.page

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kabuki.CLICK_OPERATION
import kabuki.InterceptedOperation
import kabuki.KabukiAssertionError
import kabuki.KabukiInterceptor
import kabuki.KabukiTestScope
import kabuki.KabukiUsageError
import kabuki.SearchKind
import kabuki.Tree
import kabuki.internal.requireLiveHierarchy
import kabuki.internal.runOperation
import kabuki.semantics.BackgroundColorKey
import kabuki.semantics.TestTagParamsKey
import kabuki.semantics.TintColorKey
import kabuki.treeFor
import kotlin.time.Duration

/** Enough lookalike tags to show the pattern without burying the failure message. */
private const val MAX_LOOKALIKE_TAGS = 5

/** Up to this length an enum name is assumed to be R8's work, not a person's. */
private const val MINIFIED_NAME_LENGTH = 3

/**
 * Semantics node wrapper: a matcher plus operations, each retried until the timeout.
 *
 * Retry runs on ComposeUiTest.waitUntil (v2 API), which advances the virtual clock
 * between attempts - so it awaits delayed composition coroutines and updates from
 * real background threads alike.
 *
 * The scope is resolved lazily through [scopeProvider], so screens and components
 * can declare nodes as properties before any scope exists.
 *
 * [indexInCollection] switches to collection mode (see [UiNodeCollection.at]).
 * [searchKind] tells [kabuki.TreeStrategy] which tree to resolve in, [forcedTree]
 * overrides it for this node. [host] is the page object the node belongs to, asked
 * for its container at operation time - so a component's root may be declared after
 * the nodes it scopes.
 */
@OptIn(ExperimentalTestApi::class)
public class UiNode(
    private val scopeProvider: () -> KabukiTestScope,
    private val matcher: SemanticsMatcher,
    private val description: String,
    private val timeout: Duration? = null,
    private val indexInCollection: Int? = null,
    private val diagnosticTag: String? = null,
    private val diagnosticParams: List<String> = emptyList(),
    private val searchKind: SearchKind = SearchKind.Structural,
    private val forcedTree: Tree? = null,
    private val host: NodeHost? = null,
    private val assertionDescription: String? = null,
    private val assertion: (() -> Unit)? = null,
) {
    private val scope: KabukiTestScope get() = scopeProvider()

    /** Scoped block over this node: `buyButton { assertIsEnabled(); click() }`. */
    public operator fun invoke(block: UiNode.() -> Unit) {
        block()
    }

    /**
     * A copy with its own timeout, overriding [kabuki.KabukiConfig.defaultTimeout] -
     * for a fast negative check: `withTimeout(300.milliseconds).assertDoesNotExist()`.
     */
    public fun withTimeout(timeout: Duration): UiNode {
        return copy(timeout = timeout)
    }

    /**
     * This node resolved in the MERGED tree, whatever [kabuki.KabukiConfig.treeStrategy]
     * says: `node(CARD).merged.assertTextContains("3500")` reads the text that
     * accessibility - and therefore the user - actually gets.
     */
    public val merged: UiNode
        get() {
            return copy(forcedTree = Tree.Merged)
        }

    /**
     * This node resolved in the UNMERGED tree: the physical node that was tagged.
     * Needed to reach a tag nested inside a button, or to read a text field's own
     * value without its label mixed in.
     */
    public val unmerged: UiNode
        get() {
            return copy(forcedTree = Tree.Unmerged)
        }

    // ------------------------------- actions -------------------------------

    /**
     * Clicks the node by injecting a pointer event.
     *
     * How the click is delivered is not configured here: when a platform quirk
     * calls for another way, install an interceptor once for the whole test -
     * see [ClickOnUiThread] and [ClickViaSemanticsAction].
     */
    public fun click() {
        retryOperation(CLICK_OPERATION) {
            interaction().performClick()
        }
        scope.context.waitForIdle()
    }

    /** Press and hold - a distinct gesture, not two clicks. */
    public fun longClick() {
        retryOperation("longClick") {
            interaction().performTouchInput { longClick() }
        }
        scope.context.waitForIdle()
    }

    /** Two quick taps, recognised as a double click rather than two separate ones. */
    public fun doubleClick() {
        retryOperation("doubleClick") {
            interaction().performTouchInput { doubleClick() }
        }
        scope.context.waitForIdle()
    }

    /** Appends [text] to the field's current content. See [replaceText] to overwrite. */
    public fun typeText(text: String) {
        retryOperation("typeText('$text')") {
            interaction().performTextInput(text)
        }
        scope.context.waitForIdle()
    }

    /** Replaces the whole content of the field with [text]. */
    public fun replaceText(text: String) {
        retryOperation("replaceText('$text')") {
            interaction().performTextReplacement(text)
        }
        scope.context.waitForIdle()
    }

    /** Empties the field. */
    public fun clearText() {
        retryOperation("clearText") {
            interaction().performTextClearance()
        }
        scope.context.waitForIdle()
    }

    /**
     * Scrolls the nearest scrollable ancestor until this node is visible. Needed
     * before acting on anything below the fold: a click is delivered by
     * coordinates and would otherwise land outside the window.
     */
    public fun scrollTo() {
        retryOperation("scrollTo") {
            interaction().performScrollTo()
        }
        scope.context.waitForIdle()
    }

    /** Scrolls a lazy container (LazyColumn/LazyRow/grid) to the item at [index]. */
    public fun scrollToIndex(index: Int) {
        retryOperation("scrollToIndex($index)") {
            interaction().performScrollToIndex(index)
        }
        scope.context.waitForIdle()
    }

    // ------------------------------- asserts -------------------------------

    /** The node exists in the composition (it may still be invisible or overlapped). */
    public fun assertExists() {
        retryOperation("assertExists") {
            interaction().assertExists()
        }
    }

    /**
     * The node exists AND is visible on screen. In a lazy list an item that was
     * never composed fails this - address it by index instead (see
     * [LazyList.itemAt]), or call [scrollTo] first.
     */
    public fun assertIsDisplayed() {
        retryOperation("assertIsDisplayed") {
            interaction().assertIsDisplayed()
        }
    }

    /** The node exists but is not visible (scrolled away, zero size, hidden). */
    public fun assertIsNotDisplayed() {
        retryOperation("assertIsNotDisplayed") {
            // No hierarchy check here: Compose's own assertIsNotDisplayed demands a
            // root and throws on an empty scene.
            interaction().assertIsNotDisplayed()
        }
    }

    /**
     * The node is absent from the composition. Retry works in its favour: the
     * assertion waits until the node disappears.
     */
    public fun assertDoesNotExist() {
        retryOperation("assertDoesNotExist") {
            interaction().assertDoesNotExist()
            // Nothing is absent from a scene that composed nothing.
            scope.requireLiveHierarchy()
        }
    }

    /** The node accepts input (`enabled = true`). */
    public fun assertIsEnabled() {
        retryOperation("assertIsEnabled") {
            interaction().assertIsEnabled()
        }
    }

    /** The node is present but refuses input (`enabled = false`). */
    public fun assertIsNotEnabled() {
        retryOperation("assertIsNotEnabled") {
            interaction().assertIsNotEnabled()
        }
    }

    /** Selection semantics: tabs, navigation items, selectable rows. */
    public fun assertIsSelected() {
        retryOperation("assertIsSelected") {
            interaction().assertIsSelected()
        }
    }

    /** Selection semantics, negative: the tab or row is not the selected one. */
    public fun assertIsNotSelected() {
        retryOperation("assertIsNotSelected") {
            interaction().assertIsNotSelected()
        }
    }

    /** Toggleable semantics: checkboxes and switches in the ON state. */
    public fun assertIsOn() {
        retryOperation("assertIsOn") {
            interaction().assertIsOn()
        }
    }

    /** Toggleable semantics: checkboxes and switches in the OFF state. */
    public fun assertIsOff() {
        retryOperation("assertIsOff") {
            interaction().assertIsOff()
        }
    }

    /** The node currently holds input focus. */
    public fun assertIsFocused() {
        retryOperation("assertIsFocused") {
            interaction().assertIsFocused()
        }
    }

    /** The node is focusable but not focused right now. */
    public fun assertIsNotFocused() {
        retryOperation("assertIsNotFocused") {
            interaction().assertIsNotFocused()
        }
    }

    /**
     * The node has a click action in its semantics - it is a button, not just
     * something that happens to look like one.
     */
    public fun assertHasClickAction() {
        retryOperation("assertHasClickAction") {
            interaction().assertHasClickAction()
        }
    }

    /** The node is not clickable - e.g. a label that must stay inert. */
    public fun assertHasNoClickAction() {
        retryOperation("assertHasNoClickAction") {
            interaction().assertHasNoClickAction()
        }
    }

    /**
     * Asserts the content description of the node.
     *
     * [substring] defaults to true, matching [assertTextContains]: both are
     * named "Contains" and must behave the same way. The underlying Compose
     * assertion defaults to an exact match, which would make the two
     * inconsistent - pass `substring = false` for exact matching.
     *
     * A node can carry several descriptions; the assertion passes if any of them
     * matches.
     */
    public fun assertContentDescriptionContains(
        expected: String,
        substring: Boolean = true,
        ignoreCase: Boolean = false,
    ) {
        retryOperation("assertContentDescriptionContains('$expected')") {
            interaction().assertContentDescriptionContains(
                value = expected,
                substring = substring,
                ignoreCase = ignoreCase,
            )
        }
    }

    /** The node's text matches [expected] exactly. */
    public fun assertTextEquals(expected: String) {
        // The operation is named after the PUBLIC method: a report that says
        // "assertText" sends the reader looking for a line that is not in the test.
        assertText("assertTextEquals('$expected')", expected, substring = false)
    }

    /**
     * The node's text contains [expected]. A node may carry several texts
     * (its own and merged children) - any match counts.
     */
    public fun assertTextContains(expected: String, substring: Boolean = true) {
        assertText("assertTextContains('$expected')", expected, substring)
    }

    /**
     * Asserts the resolved text color of a Text node via its text layout.
     *
     * Reliable when the color is set explicitly (the `color` parameter or the
     * style). Text colored through LocalContentColor may resolve to
     * Color.Unspecified in the layout input - publish the color explicitly in
     * that case (kabuki-semantics `testTintColor`).
     */
    public fun assertTextColor(expected: Color) {
        var actual: Color? = null
        retryOperation(
            operation = "assertTextColor($expected)",
            onTimeout = { cause, timeoutUsed ->
                KabukiAssertionError(
                    message = buildString {
                        appendLine("Node $description does not have the expected text color within $timeoutUsed.")
                        appendLine("Expected: $expected")
                        append("Actual: ${actual ?: "<no text layout>"}")
                    },
                    cause = cause,
                )
            },
        ) {
            val style = requireTextStyle()
            actual = style.color
            if (style.color != expected) {
                throw AssertionError("Text color mismatch: expected $expected, actual ${style.color}")
            }
        }
    }

    /**
     * Asserts the background color published via kabuki-semantics
     * `Modifier.testBackgroundColor(...)` in production code.
     */
    public fun assertBackgroundColor(expected: Color) {
        assertColorKey(BackgroundColorKey, "background color", expected)
    }

    /**
     * Asserts the tint color published via kabuki-semantics
     * `Modifier.testTintColor(...)` in production code.
     */
    public fun assertTintColor(expected: Color) {
        assertColorKey(TintColorKey, "tint color", expected)
    }

    // ------------------------------- read API -------------------------------

    /**
     * Resolved [TextStyle] of a Text node (color, fontSize, fontWeight, ...) -
     * obtained through the GetTextLayoutResult semantics action, with retry.
     * For custom assertions the typed API does not cover.
     */
    public fun textStyle(): TextStyle {
        var style: TextStyle? = null
        retryOperation("textStyle") {
            style = requireTextStyle()
        }
        return checkNotNull(style)
    }

    // ---------------------------- extension points ----------------------------

    /** A custom action, retried and reported under [name] like a built-in one. */
    public fun action(name: String, block: (SemanticsNodeInteraction) -> Unit) {
        retryOperation(name) {
            block(interaction())
        }
        scope.context.waitForIdle()
    }

    /**
     * A custom read, retried and reported under [name]. Null is a result, not a
     * reason to retry - only a thrown exception fails the attempt.
     */
    public fun <T> read(name: String, block: (SemanticsNodeInteraction) -> T): T {
        var result: T? = null
        var captured = false
        retryOperation(name) {
            result = block(interaction())
            captured = true
        }
        if (!captured) {
            // Without this the cast below is a bare NPE that names nothing.
            throw KabukiUsageError(
                "read('$name') produced no value: an interceptor skipped it without calling proceed().",
            )
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * Whether [block] passes, instead of failing the test. Runs silently, and waits
     * out the timeout - pair it with [withTimeout]. Usage errors are not caught.
     */
    public fun passed(block: UiNode.() -> Unit): Boolean {
        return scope.config.muted {
            try {
                block()
                true
            } catch (e: AssertionError) {
                // Swallowing IS the contract here: a probe answers instead of failing.
                false
            }
        }
    }

    /**
     * The operation counts as done only when [assertion] passes - both live inside
     * one retry, so the operation REPEATS until the effect appears:
     *
     * ```kotlin
     * buyButton.withAssertion("the dialog opens") { dialog.root.assertIsDisplayed() }.click()
     * ```
     *
     * Beware of actions that must not happen twice; a failure now costs the whole
     * timeout.
     */
    public fun withAssertion(description: String, assertion: () -> Unit): UiNode {
        return copy(assertionDescription = description, assertion = assertion)
    }

    /**
     * Clicks until [assertion] passes - the common case of [withAssertion]:
     * `buyButton.clickUntil("the dialog opens") { dialog.root.assertIsDisplayed() }`.
     */
    public fun clickUntil(description: String, assertion: () -> Unit) {
        withAssertion(description, assertion).click()
    }

    /** The raw interaction - no retry, no report. Prefer [action] and [read]. */
    public fun <T> raw(block: (SemanticsNodeInteraction) -> T): T {
        return block(interaction())
    }

    // ------------------------------- internals -------------------------------

    private fun requireTextStyle(): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        interaction().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.firstOrNull()
            ?: throw AssertionError("Node $description has no text layout (not a Text node?)")
        return layout.layoutInput.style
    }

    private fun assertColorKey(
        key: SemanticsPropertyKey<Color>,
        colorName: String,
        expected: Color,
    ) {
        var actual: Color? = null
        retryOperation(
            operation = "assert ${colorName.replace(' ', '_')}($expected)",
            onTimeout = { cause, timeoutUsed ->
                KabukiAssertionError(
                    message = buildString {
                        appendLine("Node $description does not have the expected $colorName within $timeoutUsed.")
                        appendLine("Expected: $expected")
                        val missing = "<not published - add the matching kabuki-semantics " +
                            "modifier in production code>"
                        append("Actual: " + (actual ?: missing))
                    },
                    cause = cause,
                )
            },
        ) {
            val published = interaction().fetchSemanticsNode().config.getOrNull(key)
            actual = published
            if (published != expected) {
                throw AssertionError("$colorName mismatch: expected $expected, actual $published")
            }
        }
    }

    /**
     * The ladder: check the node itself, then - unless it is a text field - the
     * MERGED view of the SAME node (its id is identical in both trees).
     *
     * Step two is what makes correct markup work: the tag sits on the button while
     * the text lives in a Text inside it, so in the unmerged tree the button has no
     * text of its own. [kabuki.TreeStrategy.contentFallback] switches the step off.
     */
    private fun assertText(operation: String, expected: String, substring: Boolean) {
        var ownText: String? = null
        var mergedText: String? = null
        var mergedConsulted = false

        retryOperation(
            operation = operation,
            onTimeout = { cause, timeoutUsed ->
                KabukiAssertionError(
                    message = buildString {
                        appendLine("Node $description does not contain expected text within $timeoutUsed.")
                        appendLine("Expected ${if (substring) "substring" else "text"}: '$expected'")
                        appendLine("Actual text: ${ownText?.let { "'$it'" } ?: "<node not found or has no text>"}")
                        // Only when it adds something: a merged view that repeats the
                        // node's own text is noise in an already long message.
                        val repeatsOwnText = mergedText != null && mergedText == ownText
                        if (mergedConsulted && !repeatsOwnText) {
                            appendLine(
                                "Merged view of the same node: " +
                                    (mergedText?.let { "'$it'" } ?: "<no text there either>"),
                            )
                        }
                        descendantWithTextHint(expected)?.let { hint -> appendLine(hint) }
                        append(treeHint())
                        append(tagHints())
                    },
                    cause = cause,
                )
            },
        ) {
            val node = interaction().fetchSemanticsNode()
            ownText = node.textOrNull()
            if (textMatches(ownText, expected, substring)) {
                return@retryOperation
            }

            mergedConsulted = mayConsultMergedView(node, ownText, substring)
            if (mergedConsulted) {
                mergedText = mergedViewOf(node.id)?.textOrNull()
                if (textMatches(mergedText, expected, substring)) {
                    return@retryOperation
                }
            }
            throw AssertionError("Text mismatch: expected '$expected', actual '${mergedText ?: ownText}'")
        }
    }

    private fun SemanticsNode.textOrNull(): String? {
        return config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text
    }

    private fun textMatches(text: String?, expected: String, substring: Boolean): Boolean {
        return when {
            text == null -> false
            substring -> text.contains(expected)
            else -> text == expected
        }
    }

    /**
     * Whether step two of the ladder applies.
     *
     * A substring check takes it even when the node HAS text: the merged view
     * appends the children's texts, so "own" becomes "own child" and the expected
     * substring can only appear there. An exact check gains nothing in that case -
     * a longer string cannot be equal either - so it is skipped, which saves a
     * fetch on every failing assertTextEquals.
     */
    private fun mayConsultMergedView(node: SemanticsNode, ownText: String?, substring: Boolean): Boolean {
        if (tree() != Tree.Unmerged || !scope.config.treeStrategy.contentFallback) {
            return false
        }
        // A text field's value belongs to the node itself, while its merged view
        // mixes in the label - an assertion about the value would then pass on the
        // label and report a field that was never filled in as correct.
        if (node.config.getOrNull(SemanticsProperties.EditableText) != null) {
            return false
        }
        return substring || ownText == null
    }

    /** The same node seen from the merged tree - node ids are shared between the two. */
    private fun mergedViewOf(id: Int): SemanticsNode? {
        return runCatching {
            scope.context
                .onAllNodes(
                    matcher = SemanticsMatcher("node id $id") { candidate -> candidate.id == id },
                    useUnmergedTree = false,
                )
                .fetchSemanticsNodes()
                .firstOrNull()
        }.getOrNull()
    }

    /**
     * Names the node the text is physically on, when it is not the one checked.
     * Turns "actual text: null" into the actual mistake: right screen, wrong node.
     */
    private fun descendantWithTextHint(expected: String): String? {
        val root = runCatching { interaction().fetchSemanticsNode() }.getOrNull() ?: return null
        val holder = findDescendantWithText(root, expected) ?: return null
        val tag = holder.config.getOrNull(SemanticsProperties.TestTag)
        val where = tag?.let { "tag '$it'" } ?: "id ${holder.id}"
        return "The text is on a DESCENDANT of this node ($where), not on the node itself."
    }

    private fun findDescendantWithText(node: SemanticsNode, expected: String): SemanticsNode? {
        return node.children.firstNotNullOfOrNull { child ->
            if (child.textOrNull()?.contains(expected) == true) {
                child
            } else {
                findDescendantWithText(child, expected)
            }
        }
    }

    /**
     * When a parametrized tag is not found, list the parameters that ARE on
     * screen under the same tag - and call out a likely argument swap. Turns
     * "node does not exist" into a message that names the actual mistake.
     */
    private fun sameTagHint(): String? {
        val tag = diagnosticTag ?: return null

        val expected = diagnosticParams.takeIf { params -> params.isNotEmpty() } ?: return null
        val present = runCatching {
            // Always the structural tree: this looks up nodes BY TAG, regardless of
            // how the failing node itself was addressed.
            val unmerged = scope.config.treeStrategy.structuralSearch == Tree.Unmerged
            scope.context
                .onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged)
                .fetchSemanticsNodes()
                .mapNotNull { node -> node.config.getOrNull(TestTagParamsKey) }
        }.getOrNull().orEmpty()

        if (present.isEmpty()) {
            return null
        }
        return buildString {
            appendLine()
            appendLine("Nodes with tag '$tag' present on screen: ${present.joinToString(", ")}")
            if (present.any { params -> params.size == expected.size && params.toSet() == expected.toSet() }) {
                append("Hint: the same values exist in a different order - arguments may be swapped.")
            }
        }
    }

    /**
     * The same entry on screen under a different class name - and the message names
     * both readings. Usually the test asks for the wrong enum: names like `SCREEN`
     * and `LIST` repeat across the enums of any real app. Rarely it is R8, which
     * renames the class half of `EnumSimpleName.ENTRY`.
     */
    private fun lookalikeTagHint(): String? {
        val tag = diagnosticTag ?: return null
        // Empty when the tag carries no dot, i.e. is not an enum tag at all.
        val entry = tag.substringAfterLast('.', missingDelimiterValue = "")
        if (entry.isEmpty()) {
            return null
        }
        val onScreen = runCatching {
            val unmerged = scope.config.treeStrategy.structuralSearch == Tree.Unmerged
            scope.context
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag), useUnmergedTree = unmerged)
                .fetchSemanticsNodes()
                .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.TestTag) }
        }.getOrNull().orEmpty()

        // The tag IS there and the operation failed for its own reasons - naming
        // lookalikes here would send the reader after a name that is already right.
        if (tag in onScreen) {
            return null
        }
        val lookalikes = onScreen
            .filter { present -> present.endsWith(".$entry") }
            .distinct()
            // A long list would bury the message it is attached to.
            .take(MAX_LOOKALIKE_TAGS)

        if (lookalikes.isEmpty()) {
            return null
        }
        return buildString {
            appendLine()
            appendLine("The same entry is on screen under another class: ${lookalikes.joinToString(", ")}")
            // Minified names get the R8 advice; readable ones almost always mean the
            // screen on display is not the one the test expects. Sending everybody
            // to proguard rules would misdirect the common case.
            if (lookalikes.all { name -> looksMinified(name) }) {
                append("Looks minified: R8 renamed the enum - keep it with -keepnames class **${tag.substringBeforeLast('.')}")
            } else {
                append("So this is either another screen than the test expects, or the wrong enum in the test.")
            }
        }
    }

    /** R8 names are short and lower-case; a real tag enum is neither. */
    private fun looksMinified(tag: String): Boolean {
        val enumName = tag.substringBeforeLast('.')
        return enumName.length <= MINIFIED_NAME_LENGTH || enumName.none { symbol -> symbol.isUpperCase() }
    }

    /**
     * Both tag diagnostics, empty when neither applies. Skipped inside a probe:
     * each hint walks the tree, and `passed { }` throws the message away.
     */
    private fun tagHints(): String {
        if (scope.config.isMuted) {
            return ""
        }
        return sameTagHint().orEmpty() + lookalikeTagHint().orEmpty()
    }

    private fun interaction(): SemanticsNodeInteraction {
        val unmerged = tree() == Tree.Unmerged
        val effective = effectiveMatcher()
        return if (indexInCollection == null) {
            scope.context.onNode(effective, useUnmergedTree = unmerged)
        } else {
            scope.context
                .onAllNodes(effective, useUnmergedTree = unmerged)[indexInCollection]
        }
    }

    /**
     * The declared matcher plus the container of the page object it belongs to.
     * Two identical components on one screen are told apart by exactly this.
     */
    internal fun effectiveMatcher(): SemanticsMatcher {
        val container = host?.containerFor(this) ?: return matcher
        return matcher.and(hasAnyAncestor(container))
    }

    private fun tree(): Tree {
        return forcedTree ?: scope.config.treeStrategy.treeFor(searchKind)
    }

    /**
     * Which tree the node was looked for in - belongs in every failure message,
     * because "no such node" and "wrong tree" look identical otherwise.
     */
    private fun treeHint(): String {
        val chosen = tree()
        val reason = if (forcedTree != null) {
            "forced on this node"
        } else {
            "${searchKind.name.lowercase()} search"
        }
        return "Tree: ${chosen.name.lowercase()} ($reason)"
    }

    private fun copy(
        timeout: Duration? = this.timeout,
        forcedTree: Tree? = this.forcedTree,
        assertionDescription: String? = this.assertionDescription,
        assertion: (() -> Unit)? = this.assertion,
    ): UiNode {
        return UiNode(
            scopeProvider = scopeProvider,
            matcher = matcher,
            description = description,
            timeout = timeout,
            indexInCollection = indexInCollection,
            diagnosticTag = diagnosticTag,
            diagnosticParams = diagnosticParams,
            searchKind = searchKind,
            forcedTree = forcedTree,
            host = host,
            assertionDescription = assertionDescription,
            assertion = assertion,
        )
    }

    /** Operation name for the report and the failure: `click until 'the dialog opens'`. */
    private fun named(operation: String): String {
        return assertionDescription?.let { "$operation until '$it'" } ?: operation
    }

    private fun retryOperation(
        operation: String,
        onTimeout: (cause: Throwable?, timeoutUsed: Duration) -> Throwable = { cause, timeoutUsed ->
            KabukiAssertionError(
                message = "Operation '${named(operation)}' on node $description failed within $timeoutUsed." +
                    "\n${treeHint()}" +
                    cause?.let { "\nLast error: ${it.message}" }.orEmpty() +
                    tagHints(),
                cause = cause,
            )
        },
        block: () -> Unit,
    ) {
        val interceptors = scope.config.interceptors
        // The chain lives INSIDE retryUntilSuccess: a replacement installed by an
        // interceptor is retried exactly like the original operation would be.
        val intercepted: () -> Unit = if (interceptors.isEmpty()) {
            block
        } else {
            { runInterceptorChain(interceptors, index = 0, operation = operation, original = block) }
        }
        // The post-assertion runs in the same attempt, after the interceptors: a
        // check outside the retry could not make the operation repeat.
        val check = assertion
        val effective: () -> Unit = if (check == null) {
            intercepted
        } else {
            {
                intercepted()
                // Answers NOW - waiting is this loop's job, not the check's.
                scope.config.withDefaultTimeout(Duration.ZERO) { check() }
            }
        }
        scope.runOperation(
            operation = named(operation),
            nodeDescription = description,
            timeout = timeout ?: scope.config.currentDefaultTimeout,
            onTimeout = onTimeout,
            block = effective,
        )
    }

    private fun runInterceptorChain(
        interceptors: List<KabukiInterceptor>,
        index: Int,
        operation: String,
        original: () -> Unit,
    ) {
        if (index == interceptors.size) {
            original()
            return
        }
        interceptors[index].intercept(
            InterceptedOperation(
                name = operation,
                nodeDescription = description,
                nodeProvider = { interaction() },
                proceedAction = {
                    runInterceptorChain(interceptors, index + 1, operation, original)
                },
            ),
        )
    }

}
