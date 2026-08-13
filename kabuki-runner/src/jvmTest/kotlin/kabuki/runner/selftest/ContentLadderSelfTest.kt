package kabuki.runner.selftest

import kabuki.KabukiAssertionError
import kabuki.TreeStrategy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the content ladder: a text assertion checks the node itself and
 * then the MERGED view of that same node.
 *
 * Without step two, correct markup fails - the tag sits on the button while the
 * text lives in the Text inside it, so in the unmerged tree the button carries no
 * text at all.
 */
class ContentLadderSelfTest : SelfTestCase() {

    @Test
    fun aTaggedButtonReadsTheTextOfItsChild() {
        runTest(name = "ladder, substring") {
            node(SelfTestTags.TREE_BUTTON).assertTextContains(TREE_LABEL_TEXT)
        }
    }

    @Test
    fun anExactAssertTakesTheLadderWhenTheNodeHasNoTextOfItsOwn() {
        runTest(name = "ladder, exact") {
            node(SelfTestTags.TREE_BUTTON).assertTextEquals(TREE_LABEL_TEXT)
        }
    }

    @Test
    fun withoutTheLadderTheSameAssertionFails() {
        runTest(
            name = "ladder off",
            config = { treeStrategy = TreeStrategy.AlwaysUnmerged },
        ) {
            // Exactly the situation that broke ~145 tests in a real project when a
            // global unmerged flag was switched on.
            assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.TREE_BUTTON)
                    .withTimeout(200.milliseconds)
                    .assertTextContains(TREE_LABEL_TEXT)
            }
        }
    }

    @Test
    fun aTextFieldValueIsNeverReadFromTheMergedView() {
        runTest(name = "field value vs label") {
            step("The empty field does not report its label as a value") {
                assertFailsWith<KabukiAssertionError> {
                    node(SelfTestTags.LABELED_INPUT)
                        .withTimeout(200.milliseconds)
                        .assertTextContains(LABELED_INPUT_LABEL)
                }
            }
            step("The label IS in the merged view - which is what the rule protects from") {
                node(SelfTestTags.LABELED_INPUT).merged.assertTextContains(LABELED_INPUT_LABEL)
            }
        }
    }

    @Test
    fun theMergedViewIsOnlyReportedWhenItSaysSomethingNew() {
        runTest(name = "no duplicate line") {
            val error = assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.TITLE)
                    .withTimeout(200.milliseconds)
                    .assertTextContains("not on this screen")
            }
            val message = error.message.orEmpty()

            // This node has its own text, and its merged view has the same one.
            // Printing it twice adds a line to a message that is long already.
            assertTrue("Actual text: 'Kabuki SelfTest'" in message, "The node's own text must be shown: $message")
            assertTrue(
                "Merged view of the same node" !in message,
                "A merged view identical to the node's own text must not be repeated: $message",
            )
        }
    }

    @Test
    fun theFailureNamesTheNodeTheTextIsActuallyOn() {
        runTest(name = "descendant hint") {
            // The screen root is a Column: it merges nothing, so neither the node
            // nor its merged view has the text - it is two levels down.
            val error = assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.SCREEN)
                    .withTimeout(200.milliseconds)
                    .assertTextContains("Kabuki SelfTest")
            }
            val message = error.message.orEmpty()

            assertTrue(
                "The text is on a DESCENDANT of this node" in message,
                "The failure must say where the text is: $message",
            )
            assertTrue(
                "tag 'SelfTestTags.TITLE'" in message,
                "The hint must name the node holding the text: $message",
            )
            assertTrue(
                "Merged view of the same node:" in message,
                "The failure must show that step two was taken: $message",
            )
        }
    }
}
