package kabuki.runner.selftest.tests

import kabuki.ClickViaSemanticsAction
import kabuki.KabukiAssertionError
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Self-test for retry on top of the v2 runComposeUiTest API.
 * Covers three kinds of asynchrony:
 * - a delayed composition coroutine (virtual clock),
 * - a state update from a real background thread (real time),
 * - an immediate reaction to a click.
 *
 * Tests inherit SelfTestCase - the environment and content come from the base class.
 */
class RetrySelfTest : SelfTestCase() {


    @Test
    fun retryWaitsForAllKindsOfAsync() = runTest(name = "Retry on top of v2 API") { app ->
        step("A block appearing after delay(1.5s) on the virtual clock") {
            node(SelfTestTags.DELAYED_BLOCK).assertIsDisplayed()
        }

        step("Click and an immediate state reaction") {
            node(SelfTestTags.COUNTER_BUTTON).click()
            node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 1")
            assertEquals(1, app.counter, "The environment is directly accessible in the test")
        }

        step("Click via the SemanticsAction strategy") {
            // Installed for this step only, so the rest of the test keeps using
            // the default pointer click.
            config.interceptors += ClickViaSemanticsAction()
            node(SelfTestTags.COUNTER_BUTTON).click()
            node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 2")
            config.interceptors.clear()
        }

        step("Loading on a real background thread - waiting in real time") {
            node(SelfTestTags.LOAD_BUTTON).click()
            node(SelfTestTags.STATUS).assertTextContains("Done")
        }

        step("Typing into a text field") {
            node(SelfTestTags.INPUT).typeText("kabuki")
            node(SelfTestTags.INPUT).assertTextContains("kabuki")
        }
    }

    @Test
    fun failedAssertProducesReadableError() = runTest(
        name = "Human-readable assertion error",
        config = { defaultTimeout = 2.seconds },
    ) {
        val error = assertFailsWith<KabukiAssertionError> {
            node(SelfTestTags.TITLE).assertTextContains("Text that is not there")
        }
        log("Error message:\n${error.message}")

        assertTrue("Expected substring: 'Text that is not there'" in error.message.orEmpty())
        assertTrue("Actual text: 'Kabuki SelfTest'" in error.message.orEmpty())
        // The dump names its tree: the same UI printed from the other tree looks
        // different enough to send the reader down the wrong path.
        assertTrue("Semantics tree (unmerged) at the moment of failure:" in error.message.orEmpty())
    }

    @Test
    fun scopedInvokeSyntaxWorks() = runTest(name = "Scoped invoke syntax") {
        // The invoke syntax works on val elements (page object style), but not on a
        // direct node(tag) { } call - the lambda is parsed as a second argument
        val counterButton = node(SelfTestTags.COUNTER_BUTTON)
        val counterValue = node(SelfTestTags.COUNTER_VALUE)

        counterButton {
            assertIsDisplayed()
            click()
        }
        counterValue {
            assertTextContains("Counter: 1")
        }
    }
}
