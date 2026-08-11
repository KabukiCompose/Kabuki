package kabuki.sample.screens

import kabuki.UiNode
import kabuki.ListItem
import kabuki.ListItemScope
import kabuki.Screen
import kabuki.sample.ui.PlaybillTags
import kabuki.sample.model.Genre

class PlaybillScreen : Screen<PlaybillScreen>() {

    override val root = node(PlaybillTags.SCREEN)

    val shimmer = node(PlaybillTags.SHIMMER)

    /** Placeholder cards that are actually composed - depends on the screen size. */
    val shimmerCards = nodeAll(PlaybillTags.SHIMMER_CARD)

    /** The same shimmer grid as a list: its published length does NOT depend on the screen size. */
    val shimmers = lazyList(PlaybillTags.SHIMMER)
    val genreFilter = node(PlaybillTags.GENRE_FILTER)
    val navBar = component(::NavBarComponent)

    /** The playbill is a lazy GRID - the LazyList DSL addresses grids the same way. */
    val cards = lazyList(PlaybillTags.LIST) {
        itemType(::PerformanceCardItem)
    }

    fun card(id: String): UiNode {
        return node(PlaybillTags.CARD, id)
    }

    fun genreOption(genre: Genre?): UiNode {
        return node(PlaybillTags.GENRE_OPTION, genre?.name ?: "ALL")
    }

    fun filterByGenre(genre: Genre?) {
        genreFilter.click()
        genreOption(genre).click()
    }
}

class PerformanceCardItem(scope: ListItemScope) : ListItem(scope) {
    val title = child(PlaybillTags.CARD_TITLE)
    val price = child(PlaybillTags.CARD_PRICE)
}
