package kabuki.runner.selftest

import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.runner.WindowMode
import kabuki.runner.runDesktopTest
import kabuki.runner.selftest.app.SelfTestApp
import kabuki.runner.selftest.app.SelfTestAppState

/**
 * The same self-test app as [SelfTestCase], but launched through [runDesktopTest]
 * so a test can choose the window mode. Only for what a device has no equivalent
 * of - everything else belongs in the shared base class.
 */
abstract class DesktopSelfTestCase {

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
