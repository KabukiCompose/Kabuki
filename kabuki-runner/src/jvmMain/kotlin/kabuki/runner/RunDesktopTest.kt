package kabuki.runner

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.TestProfile
import kabuki.asKabukiContext
import kabuki.defaultTestProfile
import kabuki.listener.TestInfo
import kabuki.listener.TestResult

/**
 * Desktop runner entry point: a headless scene (v2 skiko runner) sized and
 * scaled by the [profile], plus an optional real window with the same content
 * (see [WindowMode]).
 */
@OptIn(ExperimentalTestApi::class)
public fun runDesktopTest(
    name: String = "Kabuki Test",
    profile: TestProfile = defaultTestProfile(),
    window: WindowMode = WindowMode.Auto,
    config: KabukiConfig.() -> Unit = {},
    block: KabukiTestScope.() -> Unit,
) {
    val resolvedMode = window.resolve()
    val density = Density(profile.density)
    val sceneSize = Size(
        width = profile.windowSize.width.value * density.density,
        height = profile.windowSize.height.value * density.density,
    )
    runSkikoComposeUiTest(size = sceneSize, density = density) {
        var visibleWindow: KabukiTestWindow? = null
        val resolvedConfig = KabukiConfig().apply(config)
        val scope = KabukiTestScope(
            context = this.asKabukiContext(),
            config = resolvedConfig,
            profile = profile,
            onSetContent = { content ->
                if (resolvedMode is WindowMode.Visible && visibleWindow == null) {
                    visibleWindow = KabukiTestWindow(
                        mode = resolvedMode,
                        size = profile.windowSize,
                        // Window messages go through the same listener SPI as
                        // everything else - not straight to the console.
                        log = { message ->
                            resolvedConfig.notifyListeners { onLog(message) }
                        },
                    ).also {
                        it.launch(content)
                    }
                }
            },
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
            // The window closes even if reporting throws - with strictListeners a
            // broken listener does exactly that, and a leaked window would sit on
            // screen for the rest of the run.
            try {
                // Before closing: a listener may still want to look at the scene
                // (a screenshot on failure, for instance).
                scope.notifyTestFinish(info, result)
            } finally {
                visibleWindow?.close()
            }
        }
    }
}
