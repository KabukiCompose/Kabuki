package kabuki.sample.screens

import kabuki.page.Screen
import kabuki.sample.ui.NavTags
import kabuki.sample.ui.PerformanceTags

/**
 * Performance details. The root appears only after the loader finishes -
 * `onScreen<PerformanceScreen>` waits for it, no manual waiting needed.
 */
class PerformanceScreen : Screen<PerformanceScreen>() {

    override val root = node { withTag(PerformanceTags.SCREEN) }

    val title = node { withTag(PerformanceTags.TITLE) }
    val poster = node { withTag(PerformanceTags.POSTER) }
    val selectSeatsButton = node { withTag(PerformanceTags.SELECT_SEATS_BUTTON) }
    val reviewsButton = node { withTag(PerformanceTags.REVIEWS_BUTTON) }
    val backButton = node { withTag(NavTags.BACK_BUTTON) }
    val navBar = component(::NavBarComponent)

    /**
     * The screen scrolls, and on a phone-sized screen both buttons sit below the
     * fold - a plain click would land outside the window. Scrolling belongs here,
     * in the page object: it is a fact about the layout, not about the test.
     */
    fun openSeatPicker() {
        selectSeatsButton.scrollTo()
        selectSeatsButton.click()
    }

    fun openReviews() {
        reviewsButton.scrollTo()
        reviewsButton.click()
    }
}
