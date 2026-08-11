package kabuki.runner.selftest

import kabuki.KabukiAssertionError
import kabuki.KabukiListener
import kabuki.OperationInfo
import kabuki.OperationResult
import kabuki.StepInfo
import kabuki.StepResult
import kabuki.TestInfo
import kabuki.TestResult
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the worst moment a listener can break: while REPORTING a failure.
 * Even with strictListeners on, it must not replace the reason the test failed.
 */
class ListenerFailureSelfTest : SelfTestCase() {

    @Test
    fun aListenerBreakingOnAnOperationFailureDoesNotHideTheRealError() {
        val failure = assertFailsWith<KabukiAssertionError> {
            runTest(
                name = "listener throws on operation failure",
                config = {
                    strictListeners = true
                    listeners += ThrowingOnOperationFinish()
                },
            ) {
                node("selftest_absent").withTimeout(100.milliseconds).assertIsDisplayed()
            }
        }

        assertTrue(
            "selftest_absent" in failure.message.orEmpty(),
            "The assertion error must survive, got: ${failure.message?.lineSequence()?.firstOrNull()}",
        )
    }

    @Test
    fun aListenerBreakingOnATestFailureDoesNotHideTheRealError() {
        val failure = assertFailsWith<IllegalStateException> {
            runTest(
                name = "listener throws on test failure",
                config = {
                    strictListeners = true
                    listeners += ThrowingOnTestFinish()
                },
            ) {
                error("the real reason the test failed")
            }
        }

        assertTrue(
            failure.message == "the real reason the test failed",
            "The test's own error must reach the report, got: ${failure.message}",
        )
    }

    @Test
    fun anErrorFromAListenerDoesNotBreakAPassingTest() {
        // The ordinary path, where nothing has failed yet.
        runTest(
            name = "listener throws an Error on a healthy test",
            config = { listeners += ThrowingAssertionErrorOnStart() },
        ) {
            step("A step that works fine") {
                node(SelfTestTags.COUNTER_BUTTON).click()
            }
        }
    }

    @Test
    fun aListenerThrowingAnErrorIsIsolatedToo() {
        val failure = assertFailsWith<IllegalStateException> {
            runTest(
                name = "listener throws an Error",
                config = { listeners += ThrowingAssertionError() },
            ) {
                step("A step that fails for its own reason") {
                    error("the real reason")
                }
            }
        }

        assertTrue(
            failure.message == "the real reason",
            "An Error from a listener must not replace the real failure, got: ${failure.message}",
        )
    }

    @Test
    fun aListenerBreakingOnAStepFailureDoesNotHideTheRealError() {
        val failure = assertFailsWith<IllegalStateException> {
            runTest(
                name = "listener throws on step failure",
                config = {
                    strictListeners = true
                    listeners += ThrowingOnStepFinish()
                },
            ) {
                step("A step that fails on purpose") {
                    error("the real reason")
                }
            }
        }

        assertTrue(
            failure.message == "the real reason",
            "The step's own error must survive, got: ${failure.message}",
        )
    }
}

/** The most visible error of all: this is what a person reads in CI. */
private class ThrowingOnTestFinish : KabukiListener {
    override fun onTestFinish(test: TestInfo, result: TestResult) {
        error("listener exploded while reporting the test")
    }
}

private class ThrowingOnOperationFinish : KabukiListener {
    override fun onOperationFinish(operation: OperationInfo, result: OperationResult) {
        error("listener exploded while reporting an operation")
    }
}

/** Breaks on the ordinary path, where no failure is in flight yet. */
private class ThrowingAssertionErrorOnStart : KabukiListener {
    override fun onOperationStart(operation: OperationInfo) {
        throw AssertionError("listener assertion failed on start")
    }
}

/** Throws an Error, not an Exception - the case Exception-only isolation misses. */
private class ThrowingAssertionError : KabukiListener {
    override fun onStepFinish(step: StepInfo, result: StepResult) {
        throw AssertionError("listener assertion failed")
    }
}

private class ThrowingOnStepFinish : KabukiListener {
    override fun onStepFinish(step: StepInfo, result: StepResult) {
        error("listener exploded while reporting a step")
    }
}
