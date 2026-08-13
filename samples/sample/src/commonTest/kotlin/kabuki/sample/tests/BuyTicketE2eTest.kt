package kabuki.sample.tests

import kabuki.page.onScreen
import kabuki.sample.model.Genre
import kabuki.sample.runner.runTheaterTest
import kabuki.sample.scenarios.BuyTicketScenario
import kabuki.sample.screens.PerformanceCardItem
import kabuki.sample.screens.PerformanceScreen
import kabuki.sample.screens.PlaybillScreen
import kabuki.sample.screens.TicketItem
import kabuki.sample.screens.TicketsScreen
import kabuki.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SHARED e2e tests: this exact code runs on desktop (jvmTest, headless or with
 * a visible window) and on an Android device (androidInstrumentedTest).
 * Page objects, scenarios and steps are platform-agnostic; only runTheaterTest
 * has platform actuals.
 */
class BuyTicketE2eTest {

    @Test
    fun buyTicketEndToEnd() = runTheaterTest(
        name = "Buy ticket end-to-end",
        // The loading state is asserted below, so it must not race a timer:
        // the test releases the data itself.
        holdPlaybill = true,
    ) { app ->
        step("Playbill: shimmers first (all six), then cards arrive") {
            onScreen<PlaybillScreen> {
                shimmer.assertIsDisplayed()
                // Two different questions: how many placeholders are on screen
                // (a phone composes fewer than a desktop window) and how many
                // there are in total. Only the second one is size-independent.
                shimmerCards.assertCountAtLeast(1)
                shimmers.assertLengthEquals(6)
                app.repository.releasePlaybill()
                card("chushingura").assertIsDisplayed()
                shimmer.assertDoesNotExist()
            }
        }

        scenario(BuyTicketScenario(performanceId = "chushingura", row = 1, number = 2))

        step("Snackbar confirms the purchase, ticket lands in the environment") {
            nodeWithText("Ticket purchased", substring = true).assertIsDisplayed()
            assertEquals(1, app.tickets.size)
            assertEquals(1, app.tickets.first().row)
            assertEquals(2, app.tickets.first().number)
        }

        step("The ticket is visible on the tickets screen as a typed list item") {
            onScreen<PerformanceScreen> {
                navBar.ticketsTab.click()
            }
            onScreen<TicketsScreen> {
                navBar { ticketsTab.assertIsSelected() }
                tickets {
                    assertLengthEquals(1)
                    firstItem<TicketItem> {
                        title.assertTextContains("Chushingura")
                        seatInfo.assertTextEquals("Row 1, seat 2")
                        price.assertTextEquals("¥3500")
                    }
                }
            }
        }
    }

    @Test
    fun genreFilterViaDropdown() = runTheaterTest(name = "Genre filter via dropdown") {
        step("Wait for the playbill to load") {
            PlaybillScreen {
                card("chushingura").assertIsDisplayed()
            }
        }

        step("Pick 'Kids' in the dropdown - only the kids play remains") {
            onScreen<PlaybillScreen> {
                filterByGenre(Genre.KIDS)
                card("momotaro").assertIsDisplayed()
                card("chushingura").assertDoesNotExist()
            }
        }

        step("Reset the filter") {
            PlaybillScreen {
                filterByGenre(null)
                // The grid keeps the item that was visible under the filter in
                // view, so on a phone the first card is not composed at all.
                // Addressing it by index scrolls to it instead of hoping.
                cards.firstItem<PerformanceCardItem> {
                    title.assertTextContains("Chushingura")
                }
            }
        }
    }
}
