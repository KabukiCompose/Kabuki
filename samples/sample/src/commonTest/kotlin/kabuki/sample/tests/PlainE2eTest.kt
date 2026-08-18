package kabuki.sample.tests

import kabuki.sample.runner.runTheaterTest
import kabuki.sample.ui.PerformanceTags
import kabuki.sample.ui.PlaybillTags
import kotlin.test.Test

/**
 * The same app, written the plain way: no page objects, no steps, no scenarios -
 * just nodes.
 *
 * Everything else in this sample shows what Kabuki looks like at full size; this
 * file is here so the entry price is visible too. Page objects earn their keep in
 * a suite, not in the first test somebody writes.
 */
class PlainE2eTest {

    @Test
    fun openAPerformance() = runTheaterTest(name = "Plain test") {
        node(PlaybillTags.LIST).assertIsDisplayed()

        // The playbill arrives from a fake network, and the details screen has a
        // loader of its own - neither needs waiting for: every operation retries.
        node(PlaybillTags.CARD, "chushingura").click()

        node(PerformanceTags.TITLE).assertTextContains("Chushingura")
    }
}
