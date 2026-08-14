package kabuki.runner.selftest

import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.runner.runKabukiTest
import kabuki.runner.selftest.app.SelfTestApp
import kabuki.runner.selftest.app.SelfTestAppState
import kabuki.runner.selftest.app.SelfTestSection

/**
 * Base class for SelfTestApp tests. Built on [runKabukiTest], so every test on top
 * of it runs BOTH headless on the JVM and on a device - the library is checked
 * where its users run it, not only where it is convenient to test.
 *
 * Desktop specifics (window modes) live in `DesktopSelfTestCase`, JVM sources.
 */
abstract class SelfTestCase {

    protected fun runTest(
        name: String,
        section: SelfTestSection = SelfTestSection.All,
        config: KabukiConfig.() -> Unit = {},
        block: KabukiTestScope.(app: SelfTestAppState) -> Unit,
    ) {
        runKabukiTest(name = name, config = config) {
            val app = SelfTestAppState()
            setContent { SelfTestApp(app, section) }
            block(app)
        }
    }
}
