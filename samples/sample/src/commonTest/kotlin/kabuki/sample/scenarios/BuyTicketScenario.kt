package kabuki.sample.scenarios

import kabuki.Scenario
import kabuki.onScreen
import kabuki.sample.screens.PerformanceScreen
import kabuki.sample.screens.PlaybillScreen
import kabuki.sample.screens.PurchaseConfirmDialog
import kabuki.sample.screens.SeatPickerDialog

/**
 * Reusable scenario: from the loaded playbill to a purchased ticket.
 * Runs on every platform - scenarios only talk to page objects.
 */
fun BuyTicketScenario(
    performanceId: String,
    row: Int,
    number: Int,
) = Scenario {
    step("Open performance '$performanceId' from the playbill") {
        onScreen<PlaybillScreen> {
            card(performanceId).click()
        }
    }

    step("Open the seat picker") {
        onScreen<PerformanceScreen> {
            openSeatPicker()
        }
    }

    step("Pick row $row seat $number and confirm the purchase") {
        onScreen<SeatPickerDialog> {
            seat(row = row, number = number).click()
        }
        onScreen<PurchaseConfirmDialog> {
            buyButton.click()
        }
    }
}
