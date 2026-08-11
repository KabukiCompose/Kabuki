package kabuki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import kabuki.semantics.TestTagParamsKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kabuki.semantics.BackgroundColorKey
import kabuki.semantics.TintColorKey
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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import kotlin.time.Duration

/**
 * Semantics node wrapper: a matcher plus operations, each retried until the timeout.
 *
 * Retry is built on top of ComposeUiTest.waitUntil (v2 API) - it advances the
 * virtual clock between attempts, so it awaits both delayed composition
 * coroutines and updates coming from real background threads.
 *
 * The test scope is resolved lazily through [scopeProvider]: this lets DSL layers
 * (screens, components) declare nodes as properties before the scope exists.
 *
 * [indexInCollection] switches the node to collection mode: the matcher is
 * resolved via onAllNodes and the node at that index is targeted (see
 * [UiNodeCollection.at]).
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
) {
    private val scope: KabukiTestScope get() = scopeProvider()

    /** Scoped block over this node: `buyButton { assertIsEnabled(); click() }`. */
    public operator fun invoke(block: UiNode.() -> Unit) {
        block()
    }

    /**
     * A copy with its own timeout, overriding [KabukiConfig.defaultTimeout] -
     * for a fast negative check: `withTimeout(300.milliseconds).assertDoesNotExist()`.
     */
    public fun withTimeout(timeout: Duration): UiNode {
        return UiNode(scopeProvider, matcher, description, timeout, indexInCollection, diagnosticTag, diagnosticParams)
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
        assertText(expected, substring = false)
    }

    /**
     * The node's text contains [expected]. A node may carry several texts
     * (its own and merged children) - any match counts.
     */
    public fun assertTextContains(expected: String, substring: Boolean = true) {
        assertText(expected, substring)
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

    // ------------------------------- escape hatch -------------------------------

    /**
     * Direct access to the raw SemanticsNodeInteraction - no retry, no wrapping.
     * For everything the typed API does not cover yet.
     */
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
                        append(
                            "Actual: ${actual ?: "<not published - add the matching kabuki-semantics modifier in production code>"}",
                        )
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

    private fun assertText(expected: String, substring: Boolean) {
        var actual: String? = null
        retryOperation(
            operation = "assertText('$expected', substring=$substring)",
            onTimeout = { cause, timeoutUsed ->
                KabukiAssertionError(
                    message = buildString {
                        appendLine("Node $description does not contain expected text within $timeoutUsed.")
                        appendLine("Expected ${if (substring) "substring" else "text"}: '$expected'")
                        append("Actual text: ${actual?.let { "'$it'" } ?: "<node not found or has no text>"}")
                        sameTagHint()?.let { hint -> append(hint) }
                    },
                    cause = cause,
                )
            },
        ) {
            val node = interaction().fetchSemanticsNode()
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }
                ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
            actual = text
            val matches = when {
                text == null -> false
                substring -> text.contains(expected)
                else -> text == expected
            }
            if (!matches) {
                throw AssertionError("Text mismatch: expected '$expected', actual '$text'")
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
            scope.context
                .onAllNodes(hasTestTag(tag), useUnmergedTree = scope.config.useUnmergedTree)
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

    private fun interaction(): SemanticsNodeInteraction {
        return if (indexInCollection == null) {
            scope.context.onNode(matcher, useUnmergedTree = scope.config.useUnmergedTree)
        } else {
            scope.context
                .onAllNodes(matcher, useUnmergedTree = scope.config.useUnmergedTree)[indexInCollection]
        }
    }

    private fun retryOperation(
        operation: String,
        onTimeout: (cause: Throwable?, timeoutUsed: Duration) -> Throwable = { cause, timeoutUsed ->
            KabukiAssertionError(
                message = "Operation '$operation' on node $description failed within $timeoutUsed." +
                    (cause?.let { "\nLast error: ${it.message}" } ?: "") +
                    (sameTagHint() ?: ""),
                cause = cause,
            )
        },
        block: () -> Unit,
    ) {
        scope.notifyOperation(OperationInfo(operation = operation, node = description))
        val interceptors = scope.config.interceptors
        // The chain lives INSIDE retryUntilSuccess: a replacement installed by an
        // interceptor is retried exactly like the original operation would be.
        val effective: () -> Unit = if (interceptors.isEmpty()) {
            block
        } else {
            { runInterceptorChain(interceptors, index = 0, operation = operation, original = block) }
        }
        scope.retryUntilSuccess(
            conditionDescription = "$operation on $description",
            timeout = timeout ?: scope.config.defaultTimeout,
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
