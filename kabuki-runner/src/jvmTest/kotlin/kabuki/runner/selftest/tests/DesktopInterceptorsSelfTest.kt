package kabuki.runner.selftest.tests

import kabuki.ClickOnUiThread
import kabuki.runner.selftest.DesktopSelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Self-test for [ClickOnUiThread] - the one interceptor that is desktop-only.
 *
 * It lives in JVM sources for the same reason the interceptor does: on Android
 * the test framework refuses any action taken from the main thread, so the whole
 * idea has no counterpart there.
 */
class DesktopInterceptorsSelfTest : DesktopSelfTestCase() {

    @Test
    fun clickOnUiThreadInterceptorWorks() = runTest(
        name = "Click on the UI thread",
        config = { interceptors += ClickOnUiThread() },
    ) { app ->
        step("A click is dispatched on the UI thread") {
            node(SelfTestTags.COUNTER_BUTTON).click()
            node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 1")
            assertEquals(1, app.counter)
        }
    }
}
