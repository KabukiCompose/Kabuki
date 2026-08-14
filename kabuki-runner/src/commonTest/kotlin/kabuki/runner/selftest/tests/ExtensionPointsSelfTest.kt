package kabuki.runner.selftest.tests

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performClick
import kabuki.KabukiInterceptor
import kabuki.KabukiUsageError
import kabuki.listener.KabukiListener
import kabuki.listener.OperationInfo
import kabuki.listener.OperationResult
import kabuki.listener.StepInfo
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the extension points. They exist so that "the DSL does not cover
 * this yet" costs a line, not the retry loop and the report - what `raw` costs.
 */
class ExtensionPointsSelfTest : SelfTestCase() {

    @Test
    fun anActionRunsAndIsReportedUnderItsOwnName() {
        val recorder = ProbeRecorder()
        runTest(name = "custom action", config = { listeners += recorder }) { app ->
            node(SelfTestTags.COUNTER_BUTTON).action("tapTwice") { interaction ->
                interaction.performClick()
                interaction.performClick()
            }

            assertEquals(2, app.counter, "The action must reach the node")
        }

        // A report showing "raw" would say nothing.
        assertTrue("tapTwice" in recorder.started, "Operations seen: ${recorder.started}")
    }

    @Test
    fun aReadReturnsItsValueAndIsReported() {
        val recorder = ProbeRecorder()
        runTest(name = "custom read", config = { listeners += recorder }) {
            val text = node(SelfTestTags.COUNTER_VALUE).read("counterText") { interaction ->
                interaction.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.first()?.text
            }

            assertEquals("Counter: 0", text)
        }

        assertTrue("counterText" in recorder.started, "Operations seen: ${recorder.started}")
    }

    @Test
    fun nullIsAResultAndNotAReasonToRetry() {
        val recorder = ProbeRecorder()
        runTest(name = "read returns null", config = { listeners += recorder }) {
            // A Text node has no EditableText - the honest answer is "nothing there".
            val value = node(SelfTestTags.COUNTER_VALUE).read("editableText") { interaction ->
                interaction.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)
            }

            assertNull(value)
        }

        // Attempts, not a stopwatch: waiting a null out would show as many tries.
        assertEquals(1, recorder.attemptsOf("editableText"), "A null result must not be retried")
    }

    @Test
    fun aReadIsRetriedWhileItKeepsThrowing() {
        val recorder = ProbeRecorder()
        runTest(name = "read retries", config = { listeners += recorder }) {
            var attempt = 0
            val value = node(SelfTestTags.TITLE).read("thirdTimeLucky") {
                attempt++
                if (attempt < 3) {
                    throw AssertionError("not yet")
                }
                attempt
            }

            assertEquals(3, value)
        }

        assertEquals(3, recorder.attemptsOf("thirdTimeLucky"))
    }

    @Test
    fun aProbeAnswersInsteadOfFailingTheTest() {
        runTest(name = "probe") {
            assertTrue(node(SelfTestTags.TITLE).passed { assertIsDisplayed() })

            val missing = node(SelfTestTags.DELAYED_BLOCK)
                .withTimeout(200.milliseconds)
                .passed { assertTextEquals("nothing like this") }
            assertEquals(false, missing, "A failing probe must answer false, not throw")
        }
    }

    @Test
    fun aProbeIsSilentInTheReport() {
        val recorder = ProbeRecorder()
        runTest(name = "silent probe", config = { listeners += recorder }) {
            node(SelfTestTags.TITLE).withTimeout(200.milliseconds).passed {
                assertTextEquals("nothing like this")
            }
        }

        // Expected misses in a report teach the reader to ignore failures.
        assertTrue(
            recorder.failures.isEmpty(),
            "A probe must not report failures, got: ${recorder.failures}",
        )
        assertTrue(
            recorder.started.none { it.startsWith("assertText") },
            "A probe must not report its operations, got: ${recorder.started}",
        )
    }

    @Test
    fun aProbeStillHearsTheListenersAfterwards() {
        val recorder = ProbeRecorder()
        runTest(name = "muting is restored", config = { listeners += recorder }) {
            node(SelfTestTags.TITLE).withTimeout(200.milliseconds).passed { assertTextEquals("nope") }
            node(SelfTestTags.TITLE).assertTextContains("Kabuki SelfTest")
        }

        // Without unwinding, one probe would silence the rest of the test.
        assertTrue(
            recorder.started.any { it.startsWith("assertTextContains") },
            "Operations after a probe must be reported again, got: ${recorder.started}",
        )
    }

    @Test
    fun anInnerProbeDoesNotUnmuteTheOuterOne() {
        val recorder = ProbeRecorder()
        runTest(name = "nested probes", config = { listeners += recorder }) {
            node(SelfTestTags.TITLE).passed {
                node(SelfTestTags.TITLE).withTimeout(200.milliseconds).passed {
                    assertTextEquals("nope")
                }
                // A flag instead of a counter would be cleared by the inner probe.
                assertTextContains("Kabuki SelfTest")
            }
        }

        assertTrue(recorder.started.isEmpty(), "Nothing may be reported, got: ${recorder.started}")
    }

    @Test
    fun theReportComesBackAfterAProbeExplodes() {
        val recorder = ProbeRecorder()
        runTest(name = "muting unwinds on failure", config = { listeners += recorder }) {
            // The probe rethrows - muting must unwind anyway.
            runCatching { node(SelfTestTags.TITLE).passed { error("boom") } }
            node(SelfTestTags.TITLE).assertTextContains("Kabuki SelfTest")
        }

        assertTrue(
            recorder.started.any { it.startsWith("assertTextContains") },
            "Operations after a thrown probe must be reported, got: ${recorder.started}",
        )
    }

    @Test
    fun aStepInsideAProbeDoesNotEatANumber() {
        val recorder = ProbeRecorder()
        runTest(name = "numbering survives a probe", config = { listeners += recorder }) {
            step("first") { }
            node(SelfTestTags.TITLE).withTimeout(200.milliseconds).passed {
                step("hidden") { assertTextEquals("nope") }
            }
            step("second") { }
        }

        // "1, 3" in a report reads as a step that went missing.
        assertEquals(listOf("1", "2"), recorder.steps)
    }

    @Test
    fun aReadSkippedByAnInterceptorSaysSo() {
        runTest(
            name = "interceptor skips a read",
            config = { interceptors += KabukiInterceptor { /* never calls proceed */ } },
        ) {
            val error = assertFailsWith<KabukiUsageError> {
                node(SelfTestTags.TITLE).read("neverRuns") { it.fetchSemanticsNode() }
            }

            // Without the check this is a bare NPE from an internal cast.
            assertTrue("neverRuns" in error.message.orEmpty(), "Got: ${error.message}")
        }
    }

    @Test
    fun aProbeDoesNotSwallowUsageErrors() {
        runTest(name = "probe and usage errors") {
            // Anything but a failed check is a broken test, not an answer.
            assertFailsWith<IllegalStateException> {
                node(SelfTestTags.TITLE).passed { error("a mistake in the test itself") }
            }
        }
    }

}

/** Records what the listeners were told - the only way to check reporting. */
private class ProbeRecorder : KabukiListener {
    val started = mutableListOf<String>()
    val failures = mutableListOf<String>()
    val steps = mutableListOf<String>()
    private val attempts = mutableMapOf<String, Int>()

    override fun onOperationStart(operation: OperationInfo) {
        started += operation.operation
    }

    override fun onStepStart(step: StepInfo) {
        steps += step.label
    }

    override fun onOperationFinish(operation: OperationInfo, result: OperationResult) {
        when (result) {
            is OperationResult.Succeeded -> attempts[operation.operation] = result.attempts
            is OperationResult.Failed -> {
                attempts[operation.operation] = result.attempts
                failures += operation.operation
            }
        }
    }

    fun attemptsOf(operation: String): Int {
        return attempts[operation] ?: error("Operation '$operation' was never reported: $attempts")
    }
}
