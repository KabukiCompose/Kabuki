package kabuki.runner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A real window rendering the same composable content as the headless test scene.
 * Runs on its own single-threaded dispatcher so it never blocks the test.
 *
 * Two things that are easy to get wrong here:
 * - application(exitProcessOnExit = false) - otherwise closing the window kills the test JVM;
 * - closing via dispose() of the AWT window on the EDT - without it the window
 *   stays open after the coroutine is cancelled.
 */
internal class KabukiTestWindow(
    private val mode: WindowMode.Visible,
    private val size: DpSize,
    /**
     * Where window messages go. Routed through the listener SPI rather than
     * printed directly: with several tests running at once, an unattributed
     * line in the console cannot be traced back to its test.
     */
    private val log: (String) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kabuki-test-window").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
    private val readyLatch = CountDownLatch(1)

    @Volatile
    private var awtWindow: java.awt.Window? = null

    fun launch(content: @Composable () -> Unit) {
        scope.launch {
            runCatching {
                application(exitProcessOnExit = false) {
                    Window(
                        state = rememberWindowState(
                            size = size,
                            position = WindowPosition.Aligned(Alignment.TopStart),
                        ),
                        title = mode.title,
                        alwaysOnTop = mode.alwaysOnTop,
                        onCloseRequest = ::exitApplication,
                    ) {
                        awtWindow = window
                        content()
                        LaunchedEffect(Unit) { readyLatch.countDown() }
                    }
                }
            }.onFailure { error ->
                log("Failed to launch window: $error")
                readyLatch.countDown()
            }
        }
        val ready = readyLatch.await(10, TimeUnit.SECONDS)
        log(if (ready) "Window is ready" else "Window launch timed out")
    }

    fun close() {
        runCatching {
            SwingUtilities.invokeAndWait { awtWindow?.dispose() }
        }.onFailure { error ->
            log("Failed to dispose window: $error")
        }
        awtWindow = null
        scope.cancel()
        executor.shutdown()
        log("Window closed")
    }
}
