package kabuki

import androidx.compose.ui.test.performClick

/**
 * Performs clicks on the platform UI thread (Swing EDT).
 *
 * Works around a desktop issue where a click sent from the test thread is lost
 * while the window is still regaining focus.
 *
 * DESKTOP ONLY, and this is why it lives in JVM sources rather than in common
 * ones: on Android the test framework refuses any action taken from the main
 * thread ("Functions that involve synchronization cannot be run from the main
 * thread"), so a shared test config carrying this interceptor would fail on a
 * device. Keeping it here turns that into a compile error instead.
 */
public class ClickOnUiThread : KabukiInterceptor {
    override fun intercept(operation: InterceptedOperation) {
        if (operation.name != CLICK_OPERATION) {
            operation.proceed()
            return
        }
        operation.onUiThread { operation.node.performClick() }
    }
}
