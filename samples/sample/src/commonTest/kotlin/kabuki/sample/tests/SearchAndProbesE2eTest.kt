package kabuki.sample.tests

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kabuki.page.onScreen
import kabuki.sample.runner.runTheaterTest
import kabuki.sample.screens.PerformanceScreen
import kabuki.sample.screens.PlaybillScreen
import kabuki.sample.screens.ReviewItem
import kabuki.sample.screens.ReviewsDialog
import kabuki.sample.ui.ReviewTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * SHARED e2e tests for finding things by content and asking questions about the
 * UI: the same code runs on desktop and on a device.
 *
 * On a real screen an item's position depends on sorting and on what the fake
 * backend returned - so a test that wants "Ren's review" should say exactly that,
 * instead of counting rows.
 */
class SearchAndProbesE2eTest {

    @Test
    fun reviewsAreFoundByAuthorRatherThanByPosition() = runTheaterTest(
        name = "Find a review by its author",
    ) {
        step("Open a performance") {
            onScreen<PlaybillScreen> {
                card("chushingura").click()
            }
        }

        step("Open the reviews and make sure the dialog really opened") {
            onScreen<PerformanceScreen> {
                reviewsButton.scrollTo()
                // The click counts as done only once the modal is up: on a slow
                // device the first tap can land while the screen is still settling.
                reviewsButton.clickUntil("the reviews dialog opens") {
                    node(ReviewTags.DIALOG).assertIsDisplayed()
                }
            }
        }

        onScreen<ReviewsDialog> {
            step("The review is found by its author, wherever it sits in the list") {
                reviews.itemWhere<ReviewItem>({ withText("Ren 26") }) {
                    // The author, not just "some review": a search that returned any
                    // item would still show stars and a text.
                    author.assertTextContains("Ren 26")
                    assertEquals(25, index)
                }
            }

            step("The untyped form, and the first review is a different item") {
                reviews.itemNodeWhere { withText("Aiko 1") }.assertIsDisplayed()
                assertEquals(0, reviews.indexOfItemWhere { withText("Aiko 1") })
            }
        }
    }

    @Test
    fun theListAnswersQuestionsInsteadOfFailing() = runTheaterTest(
        name = "Probe and read the reviews",
    ) {
        step("Open the reviews") {
            onScreen<PlaybillScreen> {
                card("chushingura").click()
            }
            onScreen<PerformanceScreen> {
                openReviews()
            }
        }

        onScreen<ReviewsDialog> {
            step("Asking whether a 99th review exists must not fail the test") {
                val hasNinetyNinth = reviews.itemNodeAt(99)
                    .withTimeout(300.milliseconds)
                    .passed { assertExists() }

                assertEquals(false, hasNinetyNinth)
            }

            step("Reading the rating gives a value, not an assertion") {
                val stars = reviews.firstItem<ReviewItem>().rating.read("stars") { interaction ->
                    interaction.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                        ?.first()
                        ?.text
                }

                assertTrue(stars.orEmpty().contains("★"), "Expected stars, got: $stars")
            }
        }
    }
}
