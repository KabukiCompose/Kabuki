package kabuki.sample.screens

import kabuki.page.ListItem
import kabuki.page.ListItemScope
import kabuki.page.Screen
import kabuki.page.UiNode
import kabuki.sample.ui.ConfirmTags
import kabuki.sample.ui.SeatTags

/**
 * The full-height seat picker dialog. A dialog is a Screen from the DSL point
 * of view - it has a root and elements of its own. Seat rows are a lazy list
 * inside a modal - itemAt scrolls to off-screen rows.
 */
class SeatPickerDialog : Screen<SeatPickerDialog>() {

    override val root = node { withTag(SeatTags.DIALOG) }

    val closeButton = node { withTag(SeatTags.CLOSE_BUTTON) }

    val rows = lazyList(SeatTags.LIST) {
        itemType(::SeatRowItem)
    }

    fun seat(row: Int, number: Int): UiNode {
        // withAncestor narrows the search to the seat list - builder combination demo
        return node {
            withTag(SeatTags.SEAT, row, number)
            withAncestor { withTag(SeatTags.LIST) }
        }
    }
}

/** A row of seats; the row number is derived from the list item index. */
class SeatRowItem(scope: ListItemScope) : ListItem(scope) {

    fun seatButton(number: Int): UiNode {
        return child(SeatTags.SEAT, index + 1, number)
    }
}

class PurchaseConfirmDialog : Screen<PurchaseConfirmDialog>() {

    override val root = node { withTag(ConfirmTags.DIALOG) }

    val buyButton = node { withTag(ConfirmTags.BUY_BUTTON) }
    val cancelButton = node { withTag(ConfirmTags.CANCEL_BUTTON) }
}
