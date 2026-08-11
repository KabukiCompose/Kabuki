package kabuki

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import kabuki.semantics.LazyListItemIndexKey
import androidx.compose.ui.semantics.getOrNull
import kabuki.semantics.LazyListLengthKey
import kotlin.reflect.KClass

/**
 * Context of a single list item: the test scope, the item matcher and the index.
 * Passed into [ListItem] factories registered via [LazyListTypesBuilder.itemType].
 */
public class ListItemScope(
    /** The scope of the running test - children resolve against it. */
    public val testScope: KabukiTestScope,
    /** Matcher of this item; children are scoped under it via an ancestor match. */
    public val itemMatcher: SemanticsMatcher,
    /** Position in the list, as published by `Modifier.testListItem(index)`. */
    public val index: Int,
)

/**
 * Typed lazy list item. Declares children scoped to this item:
 *
 * ```kotlin
 * class ReviewItem(scope: ListItemScope) : ListItem(scope) {
 *     val author = child(ReviewTags.ITEM_AUTHOR)
 *     val rating = child { withTag(ReviewTags.ITEM_RATING) }
 * }
 * ```
 */
@OptIn(ExperimentalTestApi::class)
public abstract class ListItem(
    @PublishedApi internal val itemScope: ListItemScope,
) {
    /** The item node itself: click it, assert on it. */
    public val node: UiNode = UiNode(
        scopeProvider = { itemScope.testScope },
        matcher = itemScope.itemMatcher,
        description = "list item #${itemScope.index}",
    )

    /** Position of this item in the list. */
    public val index: Int
        get() {
            return itemScope.index
        }

    protected fun child(build: NodeMatcherBuilder.() -> Unit): UiNode {
        val builder = NodeMatcherBuilder().apply(build)
        return UiNode(
            scopeProvider = { itemScope.testScope },
            matcher = builder.buildMatcher().and(hasAnyAncestor(itemScope.itemMatcher)),
            description = "${builder.buildDescription()} in list item #${itemScope.index}",
            diagnosticTag = builder.diagnosticTag,
            diagnosticParams = builder.diagnosticParams,
        )
    }

    protected fun child(tag: Enum<*>, vararg params: Any): UiNode {
        return child { withTag(tag, *params) }
    }
}

/**
 * Registry of item types for a lazy list. A list can hold several kinds of
 * items, so the type is chosen at the call site: `itemAt<ReviewItem>(3)`.
 *
 * ```kotlin
 * val reviews = lazyList(ReviewTags.LIST) {
 *     itemType(::ReviewItem)
 *     itemType(::PromoBannerItem)
 * }
 * ```
 */
public class LazyListTypesBuilder {
    @PublishedApi
    internal val factories: MutableMap<KClass<out ListItem>, (ListItemScope) -> ListItem> = mutableMapOf()

    /** Registers a typed item factory: `itemType(::ReviewItem)`. */
    public inline fun <reified T : ListItem> itemType(noinline factory: (ListItemScope) -> T) {
        factories[T::class] = factory
    }
}

/**
 * Lazy list (LazyColumn/LazyRow/lazy grids) with typed items.
 *
 * Item addressing requires production code to mark items with
 * `Modifier.testListItem(index)` from kabuki-semantics; full-length assertions
 * additionally use `Modifier.testListLength(size)` on the container.
 *
 * Items are resolved lazily and scoped to THIS list via an ancestor matcher -
 * several lists on one screen (or a list inside a dialog) do not conflict.
 */
@OptIn(ExperimentalTestApi::class)
public class LazyList(
    @PublishedApi internal val scopeProvider: () -> KabukiTestScope,
    @PublishedApi internal val listMatcher: SemanticsMatcher,
    @PublishedApi internal val description: String,
    @PublishedApi internal val factories: Map<KClass<out ListItem>, (ListItemScope) -> ListItem>,
) {
    /** The list container node: assert visibility, scroll, raw access. */
    public val node: UiNode = UiNode(scopeProvider, listMatcher, description)

    /** Scoped block over the list: `reviews { assertNotEmpty(); assertLengthEquals(30) }`. */
    public operator fun invoke(block: LazyList.() -> Unit) {
        block()
    }

    /** All currently composed (visible) items of this list. */
    public fun visibleItems(): UiNodeCollection {
        return UiNodeCollection(
            scopeProvider = scopeProvider,
            matcher = anyItemMatcher(),
            description = "visible items of $description",
        )
    }

    /** At least one item is composed. Unlike [assertLengthEquals], needs no published length. */
    public fun assertNotEmpty() {
        visibleItems().assertCountAtLeast(1)
    }

    /**
     * Asserts the FULL length of the list (not just visible items) - requires
     * `Modifier.testListLength(size)` on the list container in production code.
     */
    public fun assertLengthEquals(expected: Int) {
        val scope = scopeProvider()
        var actual: Int? = null
        scope.runOperation(
            operation = "assertLengthEquals($expected)",
            nodeDescription = description,
            timeout = scope.config.defaultTimeout,
            onTimeout = { cause, timeoutUsed ->
                KabukiAssertionError(
                    message = buildString {
                        appendLine("$description does not have the expected length within $timeoutUsed.")
                        appendLine("Expected length: $expected")
                        append(
                            "Actual: " + when (actual) {
                                null -> "<not published - add Modifier.testListLength(size) " +
                                    "from kabuki-semantics on the list container>"
                                else -> actual.toString()
                            },
                        )
                    },
                    cause = cause,
                )
            },
        ) {
            val published = scope.context
                .onNode(listMatcher, useUnmergedTree = scope.config.useUnmergedTree)
                .fetchSemanticsNode()
                .config
                .getOrNull(LazyListLengthKey)
            actual = published
            if (published != expected) {
                throw AssertionError("List length mismatch: expected $expected, actual $published")
            }
        }
    }

    /** Scrolls the list so that the item at [index] gets composed. */
    public fun scrollToIndex(index: Int) {
        node.scrollToIndex(index)
    }

    /**
     * The item node at [index] WITHOUT scrolling or waiting - for negative checks:
     * `itemNodeAt(99).assertDoesNotExist()`.
     */
    public fun itemNodeAt(index: Int): UiNode {
        return UiNode(
            scopeProvider = scopeProvider,
            matcher = itemMatcherAt(index),
            description = "item #$index of $description",
        )
    }

    /**
     * Scrolls to [index], waits for the item to be displayed and runs the block
     * on the typed item. The type must be registered via [LazyListTypesBuilder.itemType].
     */
    public inline fun <reified T : ListItem> itemAt(index: Int, noinline block: T.() -> Unit = {}): T {
        scrollToIndex(index)
        val item = createItem(T::class, index)
        item.node.assertIsDisplayed()
        item.apply(block)
        return item
    }

    /**
     * The item at index 0 - shorthand for `itemAt(0)`. Scrolls to it, so it works
     * even when the list is currently showing a different part of itself.
     */
    public inline fun <reified T : ListItem> firstItem(noinline block: T.() -> Unit = {}): T {
        return itemAt(0, block)
    }

    @PublishedApi
    internal fun <T : ListItem> createItem(kClass: KClass<T>, index: Int): T {
        val factory = factories[kClass] ?: error(
            "No itemType registered for ${kClass.simpleName} in $description. " +
                "Register it: lazyList(...) { itemType(::${kClass.simpleName}) }",
        )
        @Suppress("UNCHECKED_CAST")
        return factory(ListItemScope(scopeProvider(), itemMatcherAt(index), index)) as T
    }

    @PublishedApi
    internal fun itemMatcherAt(index: Int): SemanticsMatcher {
        return SemanticsMatcher.expectValue(LazyListItemIndexKey, index).and(hasAnyAncestor(listMatcher))
    }

    private fun anyItemMatcher(): SemanticsMatcher {
        return SemanticsMatcher.keyIsDefined(LazyListItemIndexKey).and(hasAnyAncestor(listMatcher))
    }
}
