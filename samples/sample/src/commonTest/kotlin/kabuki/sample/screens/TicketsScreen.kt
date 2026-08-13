package kabuki.sample.screens

import kabuki.page.ListItem
import kabuki.page.ListItemScope
import kabuki.page.Screen
import kabuki.page.UiNode
import kabuki.sample.ui.TicketsTags

class TicketsScreen : Screen<TicketsScreen>() {

    override val root = node(TicketsTags.SCREEN)

    // withText demo: the placeholder is matched by its text, not by a tag
    val emptyPlaceholder = node { withText("No tickets yet", substring = true) }
    val navBar = component(::NavBarComponent)

    val tickets = lazyList(TicketsTags.LIST) {
        itemType(::TicketItem)
    }

    fun ticketCard(index: Int): UiNode {
        return node(TicketsTags.CARD, index)
    }
}

class TicketItem(scope: ListItemScope) : ListItem(scope) {
    val title = child(TicketsTags.ITEM_TITLE)
    val seatInfo = child(TicketsTags.ITEM_SEAT)
    val price = child(TicketsTags.ITEM_PRICE)
}
