package kabuki.runner

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.asKabukiContext
import kabuki.TestInfo
import kabuki.TestProfile
import kabuki.TestResult
import kabuki.defaultTestProfile

/**
 * Android runner entry point (instrumented tests): the same KabukiTestScope,
 * UiNode retry and DSL as on desktop, on top of the standard Android
 * runComposeUiTest. The app under test needs androidx.compose.ui:ui-test-manifest
 * (debugImplementation) so the test activity can launch.
 */
@OptIn(ExperimentalTestApi::class)
public fun runAndroidTest(
    name: String = "Kabuki Test",
    profile: TestProfile = defaultTestProfile(),
    config: KabukiConfig.() -> Unit = {},
    block: KabukiTestScope.() -> Unit,
) {
    runComposeUiTest {
        val scope = KabukiTestScope(
            context = this.asKabukiContext(),
            config = KabukiConfig().apply(config),
            profile = profile,
        )
        val info = TestInfo(name = name, profile = profile)
        scope.notifyTestStart(info)
        try {
            scope.block()
            scope.notifyTestFinish(info, TestResult.Passed)
        } catch (e: Throwable) {
            scope.notifyTestFinish(info, TestResult.Failed(e))
            throw e
        }
    }
}
