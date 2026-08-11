package kabuki.sample.screens

import kabuki.Component
import kabuki.sample.ui.NavTags

/**
 * The bottom navigation bar - a reusable component nested into screen objects.
 */
class NavBarComponent : Component<NavBarComponent>() {

    override val root = node { withTag(NavTags.NAV_BAR) }

    val playbillTab = node { withTag(NavTags.TAB_PLAYBILL) }
    val ticketsTab = node { withTag(NavTags.TAB_TICKETS) }
}
