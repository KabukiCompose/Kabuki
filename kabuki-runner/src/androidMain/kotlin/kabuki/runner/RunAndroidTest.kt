package kabuki.runner

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.TestProfile
import kabuki.asKabukiContext
import kabuki.defaultTestProfile
import kabuki.listener.TestInfo
import kabuki.listener.TestResult

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
        // The outcome is reported from ONE place. Reporting it in both the happy and
        // the failing path meant two finish events whenever the first one threw.
        var result: TestResult = TestResult.Passed
        try {
            scope.block()
        } catch (e: Throwable) {
            result = TestResult.Failed(e)
            throw e
        } finally {
            scope.notifyTestFinish(info, result)
        }
    }
}
