package kabuki

/**
 * One of the two semantics trees Compose exposes for the same UI.
 *
 * MERGED folds a button's children into the button: the Text inside is gone as a
 * node and its text belongs to the button. UNMERGED keeps every node's own
 * semantics - the only place a tag on a nested element exists.
 *
 * Neither fits everything, so Kabuki picks per search - see [TreeStrategy].
 */
public enum class Tree {
    /** Children folded into their merging parent - what Compose searches by default. */
    Merged,

    /** Every node with its own semantics - the physical tree. */
    Unmerged,
}

/**
 * How a node was addressed - this, not the operation, decides the tree.
 *
 * A tag inside a button exists only in the unmerged tree. A text search there
 * finds the Text instead of the button: no click action, so `assertHasClickAction`
 * fails while a click still works through coordinates.
 */
public enum class SearchKind {
    /** By tag or semantics key - the node was marked for tests. */
    Structural,

    /** By text or content description - by what the user sees. */
    Content,
}

/**
 * Decides which [Tree] a search looks at, per [SearchKind].
 *
 * The interface defaults ARE the [Smart] rules, so a strategy of your own only
 * overrides what it wants to change:
 *
 * ```kotlin
 * runKabukiTest(config = { treeStrategy = object : TreeStrategy {
 *     override val contentSearch = Tree.Unmerged
 * } })
 * ```
 *
 * A single node overrides the choice too - see [kabuki.page.UiNode.merged] and [kabuki.page.UiNode.unmerged].
 */
public interface TreeStrategy {

    /**
     * Tree for tags and semantics keys. Unmerged: in the merged tree a nested tag is
     * absent, so `child(...)` inside a list item would be unreachable.
     */
    public val structuralSearch: Tree
        get() {
            return Tree.Unmerged
        }

    /**
     * Tree for text and content description. Merged: in the unmerged tree the text
     * belongs to the inner Text, and the test ends up holding the wrong node.
     */
    public val contentSearch: Tree
        get() {
            return Tree.Merged
        }

    /**
     * Whether a text assertion may also read the merged view of the same node.
     *
     * This is what makes `node(BUY_BUTTON).assertTextContains("Buy")` work: the tag
     * is on the button, the text on the Text inside it. The merged view has both,
     * and it is what accessibility reads out.
     *
     * Never applied to a text field: the merged view mixes in the label, so an
     * assertion about the value would pass on the label.
     */
    public val contentFallback: Boolean
        get() {
            return true
        }

    public companion object {
        /** Rules measured on Compose 1.11: structural search unmerged, content search merged. */
        public val Smart: TreeStrategy = object : TreeStrategy {}

        /**
         * Everything merged - the default of Compose, KakaoCup and Ultron. Nested
         * tags are unreachable here; use it to reproduce a suite written against
         * plain Compose.
         */
        public val AlwaysMerged: TreeStrategy = object : TreeStrategy {
            override val structuralSearch: Tree = Tree.Merged
            override val contentSearch: Tree = Tree.Merged

            // Nothing to fall back to - every search is already merged.
            override val contentFallback: Boolean = false
        }

        /**
         * Everything unmerged - for migration and debugging. A suite arriving with a
         * global `useUnmergedTree = true` keeps working and can move to [Smart]
         * screen by screen; debugging shows the physical tree.
         */
        public val AlwaysUnmerged: TreeStrategy = object : TreeStrategy {
            override val structuralSearch: Tree = Tree.Unmerged
            override val contentSearch: Tree = Tree.Unmerged

            // The point here is "always the node you marked" - the merged view
            // would take that predictability away.
            override val contentFallback: Boolean = false
        }
    }
}

/** The tree this strategy prescribes for [search]. */
internal fun TreeStrategy.treeFor(search: SearchKind): Tree {
    return when (search) {
        SearchKind.Structural -> structuralSearch
        SearchKind.Content -> contentSearch
    }
}
