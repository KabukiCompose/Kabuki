package kabuki.page

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import kabuki.KabukiAssertionError
import kabuki.KabukiTestScope
import kabuki.SearchKind
import kabuki.Tree
import kabuki.internal.runOperation
import kabuki.treeFor
import kotlin.time.Duration

/**
 * A collection of nodes matched by one matcher (onAllNodes semantics):
 * count assertions and index access.
 *
 * Note: for lazy containers only the composed (visible) items are counted.
 * For full-length assertions of lazy lists see `LazyList.assertLengthEquals`
 * with `Modifier.testListLength` from kabuki-semantics.
 */
@OptIn(ExperimentalTestApi::class)
public class UiNodeCollection(
    private val scopeProvider: () -> KabukiTestScope,
    private val matcher: SemanticsMatcher,
    private val description: String,
    private val timeout: Duration? = null,
    private val searchKind: SearchKind = SearchKind.Structural,
    private val host: NodeHost? = null,
) {
    private val scope: KabukiTestScope get() = scopeProvider()

    /** The declared matcher plus the container of the page object it belongs to. */
    private fun effectiveMatcher(): SemanticsMatcher {
        val container = host?.containerFor(node = null) ?: return matcher
        return matcher.and(hasAnyAncestor(container))
    }

    /** Scoped block over the collection: `cards { assertCountEquals(6); first().click() }`. */
    public operator fun invoke(block: UiNodeCollection.() -> Unit) {
        block()
    }

    /** A copy of this collection with its own timeout, overriding [kabuki.KabukiConfig.defaultTimeout]. */
    public fun withTimeout(timeout: Duration): UiNodeCollection {
        return UiNodeCollection(scopeProvider, matcher, description, timeout, searchKind, host)
    }

    /** Node at [index] within the matched collection. All UiNode operations apply. */
    public fun at(index: Int): UiNode {
        return UiNode(
            scopeProvider = scopeProvider,
            matcher = matcher,
            description = "$description[#$index]",
            timeout = timeout,
            indexInCollection = index,
            searchKind = searchKind,
            host = host,
        )
    }

    /** The first matched node - shorthand for `at(0)`. */
    public fun first(): UiNode {
        return at(0)
    }

    /** Current number of matched nodes - an instant snapshot, no retry. */
    public fun count(): Int {
        val unmerged = scope.config.treeStrategy.treeFor(searchKind) == Tree.Unmerged
        return scope.context
            .onAllNodes(effectiveMatcher(), useUnmergedTree = unmerged)
            .fetchSemanticsNodes()
            .size
    }

    /**
     * Exactly [expected] nodes match, retried until they do.
     * For a lazy container this counts the COMPOSED nodes, which depends on the
     * screen size - assert the published length via `LazyList.assertLengthEquals`
     * instead when the total is what matters.
     */
    public fun assertCountEquals(expected: Int) {
        assertCount("assertCountEquals($expected)") { actual -> actual == expected }
    }

    /** At least [minimum] nodes match - the size-independent form of [assertCountEquals]. */
    public fun assertCountAtLeast(minimum: Int) {
        assertCount("assertCountAtLeast($minimum)") { actual -> actual >= minimum }
    }

    private fun assertCount(operation: String, matches: (actual: Int) -> Boolean) {
        var actual: Int? = null
        scope.runOperation(
            operation = operation,
            nodeDescription = description,
            timeout = timeout ?: scope.config.defaultTimeout,
            onTimeout = { cause, timeoutUsed ->
                KabukiAssertionError(
                    message = buildString {
                        appendLine("Collection $description did not reach the expected count within $timeoutUsed.")
                        appendLine("Expected: $operation")
                        append("Actual count: ${actual ?: "<not fetched>"}")
                    },
                    cause = cause,
                )
            },
        ) {
            val current = count()
            actual = current
            if (!matches(current)) {
                throw AssertionError("Count mismatch: $operation, actual $current")
            }
        }
    }
}
