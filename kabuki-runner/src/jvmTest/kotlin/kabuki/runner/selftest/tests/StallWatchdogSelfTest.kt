package kabuki.runner.selftest.tests

import kabuki.InterceptedOperation
import kabuki.KabukiInterceptor
import kabuki.runner.selftest.DesktopSelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the stall watchdog.
 *
 * Reads what the watchdog reports through `config.stallReporter` rather than by
 * swapping System.out: the reporter belongs to one test's config, so nothing here
 * is shared with a test running next to it.
 *
 * JVM-only because it blocks a thread, not because the watchdog is - that lives in
 * common code and behaves the same on a device.
 */
class StallWatchdogSelfTest : DesktopSelfTestCase() {

    @Test
    fun blockedOperationIsReported() {
        val warnings = Warnings()

        runCatching {
            // How it ends does not matter - the point is what was said while it
            // was stuck.
            runTest(
                name = "Blocked operation",
                config = {
                    defaultTimeout = 200.milliseconds
                    stallWarningAfter = 100.milliseconds
                    stallReporter = warnings::add
                    interceptors += BlockUntilWarned(warnings)
                },
            ) {
                node(SelfTestTags.COUNTER_BUTTON).click()
            }
        }

        val text = warnings.text()
        assertTrue(
            "[KABUKI] 'click' on tag 'SelfTestTags.COUNTER_BUTTON'" in text,
            "The watchdog named neither the operation nor the node: $text",
        )
        assertTrue(
            "has not returned after 300ms" in text,
            "The warning does not say how long the operation had: $text",
        )
    }

    @Test
    fun healthyOperationStaysQuiet() {
        val warnings = Warnings()

        runTest(
            name = "Healthy operation",
            config = {
                defaultTimeout = 100.milliseconds
                stallWarningAfter = 100.milliseconds
                stallReporter = warnings::add
            },
        ) {
            node(SelfTestTags.COUNTER_BUTTON).click()
        }
        // Past every deadline the finished operations had: a watchdog left running
        // would speak up here.
        Thread.sleep(500)

        assertFalse(warnings.any(), "The watchdog fired on an operation that answered at once: ${warnings.text()}")
    }

    @Test
    fun zeroTurnsTheWatchdogOff() {
        val warnings = Warnings()

        runCatching {
            runTest(
                name = "Watchdog off",
                config = {
                    defaultTimeout = 200.milliseconds
                    stallWarningAfter = Duration.ZERO
                    stallReporter = warnings::add
                    interceptors += SleepOnce(600)
                },
            ) {
                node(SelfTestTags.COUNTER_BUTTON).click()
            }
        }

        assertFalse(warnings.any(), "ZERO did not turn the watchdog off: ${warnings.text()}")
    }
}

/** What the watchdog said. Synchronised: it speaks from its own thread. */
private class Warnings {
    private val lines = mutableListOf<String>()

    fun add(line: String) {
        synchronized(lines) { lines += line }
    }

    fun any(): Boolean {
        return synchronized(lines) { lines.isNotEmpty() }
    }

    fun text(): String {
        return synchronized(lines) { lines.joinToString("\n") }
    }
}

/** Holds the first operation until the watchdog reports it - a blocked call that ends. */
private class BlockUntilWarned(private val warnings: Warnings) : KabukiInterceptor {
    private var blocked = false

    override fun intercept(operation: InterceptedOperation) {
        if (!blocked) {
            blocked = true
            val giveUpAt = System.nanoTime() + 5_000_000_000
            while (!warnings.any() && System.nanoTime() < giveUpAt) {
                Thread.sleep(10)
            }
        }
        operation.proceed()
    }
}

/** Blocks the first operation for a fixed time - for the case where nothing reports it. */
private class SleepOnce(private val millis: Long) : KabukiInterceptor {
    private var slept = false

    override fun intercept(operation: InterceptedOperation) {
        if (!slept) {
            slept = true
            Thread.sleep(millis)
        }
        operation.proceed()
    }
}
