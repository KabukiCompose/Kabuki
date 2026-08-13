package kabuki.listener

import kabuki.TestProfile
import kotlin.time.Duration

/** The test being run, as the runner sees it. Reported to every [KabukiListener]. */
public data class TestInfo(
    /** Human-readable name passed to `runKabukiTest(name = ...)`. */
    val name: String,
    /** Environment the test runs in: platform, OS, scene size, density. */
    val profile: TestProfile,
)

/** A single [kabuki.KabukiTestScope.step], reported when it starts and when it finishes. */
public data class StepInfo(
    /** Hierarchical number: "1", "1.2", "1.2.1"... - depth is the nesting level. */
    val label: String,
    /** The text the test author wrote: `step("Open the seat picker")`. */
    val description: String,
)

/** Outcome of a single [kabuki.KabukiTestScope.step], reported to listeners. */
public sealed interface StepResult {
    /** The step body completed without throwing. */
    public data object Passed : StepResult

    /** The step body threw - [error] is rethrown after the listeners are notified. */
    public data class Failed(val error: Throwable) : StepResult
}

/** A node operation. Reported once at the start and once at the end of its retry loop. */
public data class OperationInfo(
    /** e.g. "click", "assertIsDisplayed", "scrollToIndex(25)". */
    val operation: String,
    /** Node description, e.g. "tag 'PlaybillTags.SCREEN'". */
    val node: String,
)

/**
 * Outcome of a node operation. [attempts] separates an instant success from one
 * the UI made us wait for - both are green, but only one is fast.
 */
public sealed interface OperationResult {
    /** How many times the operation was actually executed before it finished. */
    public val attempts: Int

    /** Wall-clock time from the first attempt to the last, real time. */
    public val duration: Duration

    /** The operation succeeded, possibly after several attempts. */
    public data class Succeeded(
        override val attempts: Int,
        override val duration: Duration,
    ) : OperationResult

    /** The operation never succeeded within its timeout; [error] is about to be thrown. */
    public data class Failed(
        val error: Throwable,
        override val attempts: Int,
        override val duration: Duration,
    ) : OperationResult
}

/** Outcome of a whole test, reported to listeners by the runner. */
public sealed interface TestResult {
    /** The test body completed without throwing. */
    public data object Passed : TestResult

    /** The test body threw - [error] is what the test framework will report. */
    public data class Failed(val error: Throwable) : TestResult
}

/**
 * SPI for test lifecycle events: reporting (Allure), logging, metrics.
 * Register via config: `config = { listeners += MyListener() }`.
 * [ConsoleListener] is installed by default; clear [kabuki.KabukiConfig.listeners]
 * to remove it.
 */
public interface KabukiListener {
    /** The runner has set up the environment; the test body has not started yet. */
    public fun onTestStart(test: TestInfo) {}

    /** A step is entered. Nested steps arrive between their parent's start and finish. */
    public fun onStepStart(step: StepInfo) {}

    /**
     * A step is left, successfully or not. Fired whenever the step body ran - the
     * one case without it is a listener throwing on [onStepStart] under
     * [kabuki.KabukiConfig.strictListeners], where the step never starts.
     */
    public fun onStepFinish(step: StepInfo, result: StepResult) {}

    /** A node operation is about to start - before the first attempt. */
    public fun onOperationStart(operation: OperationInfo) {}

    /**
     * A node operation is over. [result] carries what only the retry loop knows:
     * how many attempts it took and how long it ran.
     */
    public fun onOperationFinish(operation: OperationInfo, result: OperationResult) {}

    /** Free-form messages from KabukiTestScope.log and runners. */
    public fun onLog(message: String) {}

    /** The test body is over and the environment is about to be torn down. */
    public fun onTestFinish(test: TestInfo, result: TestResult) {}
}

/**
 * Default console output.
 *
 * Output of ONE test is buffered and printed as a block when the test finishes:
 * Kabuki encourages running tests in parallel inside one JVM, and line by line two
 * tests interleave with nothing saying which line belongs to which.
 *
 * Two ways out of the buffer:
 * - [streaming] prints each line as it happens, prefixed with the test name - for
 *   debugging one test, or when a test might hang and a buffer would show nothing;
 * - a FAILED step flushes immediately, so the context of a failure is on screen
 *   before the teardown.
 *
 * [verbose] adds every node operation. [out] is the sink, replaceable so the
 * listener itself can be tested.
 *
 * NOT thread-safe: it holds one test's buffer, so every test gets its own instance.
 */
public class ConsoleListener(
    private val verbose: Boolean = false,
    private val streaming: Boolean = false,
    private val out: (line: String) -> Unit = { line -> println(line) },
) : KabukiListener {

    private val buffered = mutableListOf<String>()
    private var testName: String = ""
    private var flushed: Boolean = false

    override fun onTestStart(test: TestInfo) {
        // Reset: one instance may serve several tests, and a failure in an earlier
        // one would otherwise leave buffering switched off for good.
        testName = test.name
        buffered.clear()
        flushed = false

        if (streaming) {
            out(prefixed("STARTING TEST: ${test.name} (${test.profile.platform}/${test.profile.os})"))
        }
    }

    override fun onStepStart(step: StepInfo) {
        emit("[STEP ${step.label}] ${step.description}")
    }

    override fun onStepFinish(step: StepInfo, result: StepResult) {
        if (result is StepResult.Failed) {
            emit("[STEP ${step.label} FAILED] ${step.description}")
            flush()
        }
    }

    override fun onOperationFinish(operation: OperationInfo, result: OperationResult) {
        if (!verbose) {
            return
        }
        // Attempts are reported only when there was a wait: "1 attempt" is noise.
        val retries = if (result.attempts > 1) " after ${result.attempts} attempts" else ""
        val outcome = if (result is OperationResult.Failed) "FAILED" else "ok"
        emit("  ${operation.operation} on ${operation.node} - $outcome$retries")
    }

    override fun onLog(message: String) {
        emit(message)
    }

    override fun onTestFinish(test: TestInfo, result: TestResult) {
        // Only the first line of the failure: the rest is usually the semantics
        // tree dump, which belongs in the test report, not in the header.
        val verdict = when (result) {
            is TestResult.Passed -> "PASSED"
            is TestResult.Failed -> "FAILED - " + result.error.message?.lineSequence()?.firstOrNull().orEmpty()
        }

        if (streaming || flushed) {
            out(prefixed("TEST $verdict"))
            return
        }

        out("[KABUKI] --- ${test.name} --- $verdict (${test.profile.platform}/${test.profile.os})")
        buffered.forEach { line -> out("[KABUKI]   $line") }
        buffered.clear()
    }

    private fun emit(line: String) {
        if (streaming || flushed) {
            out(prefixed(line))
        } else {
            buffered += line
        }
    }

    /** Pours out what was collected so far and keeps printing directly from now on. */
    private fun flush() {
        if (flushed) {
            return
        }
        flushed = true
        buffered.forEach { line -> out(prefixed(line)) }
        buffered.clear()
    }

    private fun prefixed(line: String): String {
        return if (testName.isEmpty()) "[KABUKI] $line" else "[KABUKI][$testName] $line"
    }
}
