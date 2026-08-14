package kabuki.runner.selftest.tests

import kabuki.KabukiAssertionError
import kabuki.TreeStrategy
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kabuki.runner.selftest.app.TREE_LABEL_TEXT
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the merged/unmerged rules. Every test here pins down a MEASURED
 * fact about Compose 1.11, not a preference: the tests under a non-default
 * strategy exist to show what the default protects from - remove the rule and
 * they are the ones that keep passing while the others break.
 *
 * The probe is [SelfTestTags.TREE_BUTTON]: a clickable node whose text lives in a
 * tagged child.
 */
class TreeStrategySelfTest : SelfTestCase() {

    @Test
    fun tagInsideAClickableIsReachable() {
        runTest(name = "nested tag, smart strategy") {
            node(SelfTestTags.TREE_BUTTON_LABEL).assertExists()
        }
    }

    @Test
    fun theSameTagDoesNotExistInTheMergedTree() {
        runTest(
            name = "nested tag, always merged",
            config = { treeStrategy = TreeStrategy.AlwaysMerged },
        ) {
            // Not "it is hard to find" - the node is ABSENT. This is why structural
            // search cannot default to the merged tree: child(...) inside a list
            // item would stop being addressable at all.
            node(SelfTestTags.TREE_BUTTON_LABEL).assertDoesNotExist()
        }
    }

    @Test
    fun searchByTextFindsTheClickableNodeRatherThanTheText() {
        runTest(name = "text search, smart strategy") {
            node { withText(TREE_LABEL_TEXT) }.assertHasClickAction()
        }
    }

    @Test
    fun searchByTextInTheUnmergedTreeFindsTheTextItself() {
        runTest(
            name = "text search, always unmerged",
            config = { treeStrategy = TreeStrategy.AlwaysUnmerged },
        ) {
            // The quiet trap: the same call returns the inner Text, which has no
            // click action. A click on it still works through coordinates, so the
            // test only fails later - on an assertion about the node's role.
            node { withText(TREE_LABEL_TEXT) }.assertHasNoClickAction()
        }
    }

    @Test
    fun aNodeCanOverrideTheTreeBothWays() {
        runTest(name = "per-node override") {
            step("merged: the nested tag is not there") {
                node(SelfTestTags.TREE_BUTTON_LABEL).merged.assertDoesNotExist()
            }
            step("unmerged: text search lands on the Text") {
                node { withText(TREE_LABEL_TEXT) }.unmerged.assertHasNoClickAction()
            }
        }
    }

    @Test
    fun aTagBeatsTextInTheSameMatcher() {
        runTest(name = "mixed matcher") {
            // Both conditions hold for the inner Text, which exists only in the
            // unmerged tree - so adding text must not flip the node to a content
            // search, or this stops resolving.
            node {
                withTag(SelfTestTags.TREE_BUTTON_LABEL)
                withText(TREE_LABEL_TEXT)
            }.assertExists()
        }
    }

    @Test
    fun textSearchUnderAMergingAncestorNeedsTheUnmergedTree() {
        runTest(name = "text under a merging ancestor") {
            // A search by text goes to the merged tree, where the button IS the node
            // holding the text - so it is nobody's ancestor and the matcher resolves
            // to nothing. The combination reads perfectly sensible, which is what
            // makes it worth pinning down.
            assertFailsWith<KabukiAssertionError> {
                node {
                    withText(TREE_LABEL_TEXT)
                    withAncestor { withTag(SelfTestTags.TREE_BUTTON) }
                }.withTimeout(200.milliseconds).assertExists()
            }

            // In the physical tree the Text is a child of the button, as written.
            node {
                withText(TREE_LABEL_TEXT)
                withAncestor { withTag(SelfTestTags.TREE_BUTTON) }
            }.unmerged.assertExists()
        }
    }

    @Test
    fun theFailureMessageNamesTheTreeAndWhy() {
        runTest(name = "tree in diagnostics") {
            val structural = assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.TITLE).withTimeout(200.milliseconds).assertTextEquals("not there")
            }
            val content = assertFailsWith<KabukiAssertionError> {
                node { withText("no such text on screen") }
                    .withTimeout(200.milliseconds)
                    .assertExists()
            }

            // Without this line "no such node" and "right node, wrong tree" read
            // identically.
            assertTrue(
                "Tree: unmerged (structural search)" in structural.message.orEmpty(),
                "Structural failure must name its tree: ${structural.message}",
            )
            assertTrue(
                "Tree: merged (content search)" in content.message.orEmpty(),
                "Content failure must name its tree: ${content.message}",
            )
        }
    }

    @Test
    fun anOverriddenTreeIsReportedAsForced() {
        runTest(name = "forced tree in diagnostics") {
            val error = assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.TREE_BUTTON_LABEL)
                    .merged
                    .withTimeout(200.milliseconds)
                    .assertExists()
            }

            // The reason matters as much as the tree: it tells the reader whether
            // the strategy chose it or the test did.
            assertTrue(
                "Tree: merged (forced on this node)" in error.message.orEmpty(),
                "A forced tree must be reported as forced: ${error.message}",
            )
        }
    }
}
