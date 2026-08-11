package kabuki.runner.selftest

import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.runner.WindowMode
import kabuki.runner.runDesktopTest

/**
 * Base class for SelfTestApp tests - also an example of a desktop-specific test
 * case built directly on top of [runDesktopTest] (the generic KabukiTestCase is
 * platform-agnostic and therefore knows nothing about window modes).
 */
abstract class SelfTestCase {

    protected fun runTest(
        name: String,
        window: WindowMode = WindowMode.Headless,
        config: KabukiConfig.() -> Unit = {},
        block: KabukiTestScope.(app: SelfTestAppState) -> Unit,
    ) {
        runDesktopTest(name = name, window = window, config = config) {
            val app = SelfTestAppState()
            setContent { SelfTestApp(app) }
            block(app)
        }
    }
}
