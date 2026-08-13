package kabuki.runner.selftest

import kabuki.listener.KabukiListener
import kabuki.listener.StepInfo
import kabuki.listener.StepResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Self-test for step numbering and the listener SPI.
 *
 * Numbering is what a reader of a report navigates by: nesting depth has to show
 * up in the label (1, 1.1, 1.2), and a step that failed must not renumber the
 * steps after it.
 */
class StepNumberingSelfTest : SelfTestCase() {

    @Test
    fun nestedStepsAreNumberedByDepthAndSurviveAFailedSibling() {
        val recorder = StepRecorder()

        runTest(name = "step numbering", config = { listeners += recorder }) {
            step("first") {
                step("nested one") {}

                // A failed step must not shift the numbering of the ones after it.
                assertFailsWith<IllegalStateException> {
                    step("nested two fails") { error("boom") }
                }

                step("nested three") {}
            }
            step("second") {}
        }

        assertEquals(
            listOf("1", "1.1", "1.2", "1.3", "2"),
            recorder.startedLabels,
            "Depth must be reflected in the label, and a failed step must not renumber its siblings",
        )
    }

    @Test
    fun theListenerSeesWhetherAStepPassedOrFailed() {
        val recorder = StepRecorder()

        runTest(name = "step results", config = { listeners += recorder }) {
            step("passing") {}
            assertFailsWith<IllegalStateException> {
                step("failing") { error("boom") }
            }
        }

        assertEquals(
            listOf("1" to "passed", "2" to "failed"),
            recorder.finishedResults,
            "Each step must be reported with its own outcome",
        )
    }
}

/** Collects what the listener SPI actually reports, in order. */
private class StepRecorder : KabukiListener {
    val startedLabels: MutableList<String> = mutableListOf()
    val finishedResults: MutableList<Pair<String, String>> = mutableListOf()

    override fun onStepStart(step: StepInfo) {
        startedLabels += step.label
    }

    override fun onStepFinish(step: StepInfo, result: StepResult) {
        val outcome = when (result) {
            is StepResult.Passed -> "passed"
            is StepResult.Failed -> "failed"
        }
        finishedResults += step.label to outcome
    }
}
