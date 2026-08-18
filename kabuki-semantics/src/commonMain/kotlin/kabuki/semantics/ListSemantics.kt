package kabuki.semantics

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

/**
 * Index of an item inside a lazy list. There is no standard way to address a
 * lazy list item by index from tests, so production code marks items:
 *
 * ```kotlin
 * itemsIndexed(reviews) { index, review ->
 *     ReviewCard(review, modifier = Modifier.testListItem(index))
 * }
 * ```
 *
 * Tests then use `lazyList(...) { itemType(...) }` and `itemAt(index)`.
 */
public val LazyListItemIndexKey: SemanticsPropertyKey<Int> = SemanticsPropertyKey("KabukiLazyListItemIndex")

/**
 * Total item count of a lazy list, set on the list container. Lazy lists only
 * compose visible items, so tests cannot count the rest - production code
 * publishes the full length explicitly via [testListLength].
 */
public val LazyListLengthKey: SemanticsPropertyKey<Int> = SemanticsPropertyKey("KabukiLazyListLength")

/** Marks a lazy list item with its [index]. See [LazyListItemIndexKey]. */
public fun Modifier.testListItem(index: Int): Modifier {
    return semantics { this[LazyListItemIndexKey] = index }
}

/** Publishes the full [length] of a lazy list on its container. See [LazyListLengthKey]. */
public fun Modifier.testListLength(length: Int): Modifier {
    return semantics { this[LazyListLengthKey] = length }
}
