package kabuki.sample.tests

import kabuki.page.onScreen
import kabuki.sample.runner.runTheaterTest
import kabuki.sample.screens.PerformanceCardItem
import kabuki.sample.screens.PlaybillScreen
import kotlin.test.Test

/**
 * SHARED tests for the merged/unmerged rules on a real app - the library's own
 * self-tests run on the JVM only, so without these the rules are unverified on
 * Android, where the semantics trees are built by different platform code.
 *
 * Everything here is also the shortest demonstration of what the rules buy: not
 * one of these calls mentions a tree.
 */
class SemanticsTreeE2eTest {

    @Test
    fun theTreeRulesHoldOnThisPlatform() = runTheaterTest(name = "Semantics tree rules") {
        onScreen<PlaybillScreen> {
            step("A tagged card reads the text of its children") {
                // The tag is on the Card, the texts are in the Text nodes inside it.
                // In the unmerged tree the card has no text of its own, so this only
                // passes because a text assertion also checks the merged view.
                card("chushingura").assertTextContains("Chushingura")
            }

            step("A tag INSIDE the card is addressable") {
                // The mirror image: in the merged tree this node does not exist at
                // all, which is why structural search stays unmerged.
                cards.firstItem<PerformanceCardItem> {
                    title.assertTextContains("Chushingura")
                }
            }
        }

        step("A node found by its text is the clickable card, not the Text inside it") {
            node { withText("Chushingura", substring = true) }.assertHasClickAction()
        }
    }
}
