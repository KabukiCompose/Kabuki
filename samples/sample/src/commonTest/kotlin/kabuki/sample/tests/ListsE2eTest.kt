package kabuki.sample.tests

import kabuki.page.onScreen
import kabuki.sample.runner.runTheaterTest
import kabuki.sample.screens.PerformanceCardItem
import kabuki.sample.screens.PerformanceScreen
import kabuki.sample.screens.PlaybillScreen
import kabuki.sample.screens.PurchaseConfirmDialog
import kabuki.sample.screens.ReviewItem
import kabuki.sample.screens.ReviewsDialog
import kabuki.sample.screens.SeatPickerDialog
import kabuki.sample.screens.SeatRowItem
import kabuki.sample.ui.PosterBackground
import kabuki.sample.ui.RatingGold
import kotlin.test.Test

/**
 * SHARED lazy list e2e tests: typed items, scrolling by index, lists inside
 * modals, full-length assertions, negative checks, grid addressing and
 * color/style assertions - one code path for desktop and Android.
 */
class ListsE2eTest {

    @Test
    fun reviewsListInModal() = runTheaterTest(name = "Reviews lazy list in a modal") {
        step("Open a performance and its reviews") {
            onScreen<PlaybillScreen> {
                card("chushingura").click()
            }
            onScreen<PerformanceScreen> {
                poster.assertBackgroundColor(PosterBackground)
                openReviews()
            }
        }

        onScreen<ReviewsDialog> {
            step("List is loaded: visible items and the full published length") {
                reviews {
                    assertNotEmpty()
                    assertLengthEquals(30)
                }
            }

            step("First item: typed children, text color from the layout") {
                reviews.firstItem<ReviewItem> {
                    author.assertTextContains("Aiko 1")
                    rating.assertTextEquals("★☆☆☆☆")
                    rating.assertTextColor(RatingGold)
                }
            }

            step("Item #25 requires scrolling") {
                reviews.itemAt<ReviewItem>(25) {
                    author.assertTextContains("Ren 26")
                    text.assertIsDisplayed()
                }
            }

            step("Negative check: item #99 does not exist") {
                reviews.itemNodeAt(99).assertDoesNotExist()
            }

            step("Close the dialog") {
                closeButton.click()
            }
        }

        onScreen<PerformanceScreen> {
            title.assertTextContains("Chushingura")
        }
    }

    @Test
    fun seatRowsLazyListInModal() = runTheaterTest(name = "Seat rows lazy list in a modal") {
        step("Open the seat picker") {
            onScreen<PlaybillScreen> {
                card("yotsuya").click()
            }
            onScreen<PerformanceScreen> {
                openSeatPicker()
            }
        }

        onScreen<SeatPickerDialog> {
            step("Rows list: full length is published") {
                rows {
                    assertNotEmpty()
                    assertLengthEquals(10)
                }
            }

            step("Scroll to the last row via itemAt and pick a seat") {
                rows.itemAt<SeatRowItem>(9) {
                    seatButton(number = 5).click()
                }
            }
        }

        step("Confirmation shows the picked seat, then cancel") {
            onScreen<PurchaseConfirmDialog> {
                root.assertIsDisplayed()
                cancelButton.click()
            }
        }

        onScreen<SeatPickerDialog> {
            step("Back in the seat picker after cancel") {
                rows.node.assertIsDisplayed()
            }
        }
    }

    @Test
    fun playbillGridAddressing() = runTheaterTest(name = "Lazy GRID item addressing") {
        onScreen<PlaybillScreen> {
            step("Grid is loaded: full length is published") {
                cards {
                    assertNotEmpty()
                    assertLengthEquals(6)
                }
            }

            step("Typed items address grid cells by index") {
                cards.firstItem<PerformanceCardItem> {
                    title.assertTextContains("Chushingura")
                    price.assertTextEquals("from ¥3500")
                }
                cards.itemAt<PerformanceCardItem>(5) {
                    title.assertTextContains("Momotaro")
                }
            }

            step("Negative check: cell #6 does not exist") {
                cards.itemNodeAt(6).assertDoesNotExist()
            }

            step("Grid cell click opens the performance") {
                cards.itemAt<PerformanceCardItem>(5) {
                    node.click()
                }
            }
        }

        onScreen<PerformanceScreen> {
            title.assertTextContains("Momotaro")
        }
    }
}
