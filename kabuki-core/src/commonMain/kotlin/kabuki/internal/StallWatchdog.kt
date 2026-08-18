package kabuki.internal

import kabuki.KabukiConfig
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One scope for all watchdogs - a test fires hundreds of operations. Not on the
 * test thread, which is the one that gets stuck. SupervisorJob so that one failed
 * watchdog does not cancel the scope and silence every later operation.
 */
private val watchdogScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

/**
 * Says out loud that an operation stopped answering.
 *
 * The timeout is checked BETWEEN attempts, so a platform call that blocks inside
 * one never gives control back and the test neither fails nor finishes - measured
 * on API 25, where a scroll blocked in the emulator's graphics stack. Such a call
 * cannot be interrupted, so this only reports.
 */
internal fun startStallWatchdog(
    config: KabukiConfig,
    operation: String,
    nodeDescription: String,
    timeout: Duration,
): Job? {
    val grace = config.stallWarningAfter
    if (grace <= Duration.ZERO) {
        return null
    }
    val deadline = timeout + grace
    // Read on the test thread, which owns the config.
    val reporter = config.stallReporter
    return watchdogScope.launch {
        delay(deadline)
        val warning = "[KABUKI] '$operation' on $nodeDescription has not returned after $deadline. " +
            "A blocked platform call cannot be interrupted, so this test will not fail " +
            "on its own - take a thread dump to see where it is stuck."
        try {
            reporter(warning)
        } catch (e: Throwable) {
            // Isolated like the listeners, but here it is survival: an exception
            // leaving a coroutine reaches the thread's uncaught handler, and on
            // Android that ends the process running the tests.
            println("[KABUKI] stallReporter threw ${e::class.simpleName}: ${e.message}")
            println(warning)
        }
    }
}
