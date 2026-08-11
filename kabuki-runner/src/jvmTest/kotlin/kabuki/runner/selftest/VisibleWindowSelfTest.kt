package kabuki.runner.selftest

import kabuki.runner.WindowMode
import kotlin.test.Test

/**
 * Self-test for a real window running alongside the headless scene.
 * The test drives the headless scene while the window renders the same
 * environment - you can watch the counter grow. Verifies that:
 * - the window launches without blocking the test,
 * - the window closes in teardown without hanging the JVM,
 * - the environment created by the base class is shared between the two compositions.
 */
class VisibleWindowSelfTest : SelfTestCase() {



    @Test
    fun visibleWindowMirrorsHeadlessScene() = runTest(
        name = "Visible window alongside headless",
        window = WindowMode.Visible(title = "Kabuki SelfTest - watch the counter"),
    ) {
        step("Clicking in the headless scene - the window mirrors the state") {
            repeat(3) {
                node(SelfTestTags.COUNTER_BUTTON).click()
                Thread.sleep(400) // for the eyes only: without a pause the window flashes by
            }
            node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 3")
        }

        step("Async loading is visible in the window") {
            node(SelfTestTags.LOAD_BUTTON).click()
            node(SelfTestTags.STATUS).assertTextContains("Done")
            Thread.sleep(600) // let the eyes see "Done"
        }
    }

    @Test
    fun autoModeFallsBackByEnvironment() = runTest(
        name = "Auto mode",
        window = WindowMode.Auto,
    ) {
        node(SelfTestTags.TITLE).assertTextContains("Kabuki SelfTest")
    }
}
