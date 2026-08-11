package kabuki.runner.selftest

import kabuki.ConsoleListener
import kabuki.OperationInfo
import kabuki.OperationResult
import kabuki.Profiles
import kabuki.StepInfo
import kabuki.StepResult
import kabuki.TestInfo
import kabuki.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the console output policy. Pure logic, no UI: the listener
 * writes into an injected sink instead of stdout.
 *
 * Line-by-line printing makes the log unusable as soon as tests run in parallel
 * inside one JVM - nothing in a line says which test produced it.
 */
class ConsoleListenerSelfTest {

    private val info = TestInfo(name = "Buy a ticket", profile = Profiles.Desktop.Default)

    @Test
    fun bufferedOutputArrivesAsOneBlockAtTheEnd() {
        val lines = mutableListOf<String>()
        val listener = ConsoleListener(out = { line -> lines += line })

        listener.onTestStart(info)
        listener.onStepStart(StepInfo(label = "1", description = "Open the playbill"))
        listener.onStepFinish(StepInfo(label = "1", description = "Open the playbill"), StepResult.Passed)

        assertTrue(
            lines.isEmpty(),
            "Nothing may be printed before the test finishes - that is the whole point of buffering. Got: $lines",
        )

        listener.onTestFinish(info, TestResult.Passed)

        assertEquals(2, lines.size, "Expected a header plus one step line, got: $lines")
        assertTrue(lines[0].contains("Buy a ticket") && lines[0].contains("PASSED"), lines[0])
        assertTrue(lines[1].contains("Open the playbill"), lines[1])
    }

    @Test
    fun streamingPrintsImmediatelyAndNamesTheTest() {
        val lines = mutableListOf<String>()
        val listener = ConsoleListener(streaming = true, out = { line -> lines += line })

        listener.onTestStart(info)
        listener.onStepStart(StepInfo(label = "1", description = "Open the playbill"))

        assertEquals(2, lines.size, "Streaming must print as it goes, got: $lines")
        assertTrue(
            lines.all { line -> line.contains("Buy a ticket") },
            "Every streamed line needs the test name, otherwise parallel runs are unreadable: $lines",
        )
    }

    @Test
    fun aFailedStepFlushesTheBufferImmediately() {
        val lines = mutableListOf<String>()
        val listener = ConsoleListener(out = { line -> lines += line })

        listener.onTestStart(info)
        listener.onStepStart(StepInfo(label = "1", description = "Open the playbill"))
        assertTrue(lines.isEmpty())

        listener.onStepFinish(
            StepInfo(label = "1", description = "Open the playbill"),
            StepResult.Failed(IllegalStateException("boom")),
        )

        assertTrue(
            lines.any { line -> line.contains("Open the playbill") } &&
                lines.any { line -> line.contains("FAILED") },
            "The context of a failure must reach the console at once, got: $lines",
        )
    }

    @Test
    fun onlyTheFirstLineOfAFailureGoesIntoTheHeader() {
        val lines = mutableListOf<String>()
        val listener = ConsoleListener(out = { line -> lines += line })
        val error = AssertionError("Node not displayed\nSemantics tree at the moment of failure:\n|-Node #1")

        listener.onTestStart(info)
        listener.onTestFinish(info, TestResult.Failed(error))

        assertTrue(
            lines.none { line -> line.contains("Node #1") },
            "A whole tree dump in the console header is noise, not information: $lines",
        )
        assertTrue(lines.any { line -> line.contains("Node not displayed") }, "$lines")
    }

    @Test
    fun oneInstanceCanServeSeveralTests() {
        val lines = mutableListOf<String>()
        val listener = ConsoleListener(out = { line -> lines += line })
        val second = TestInfo(name = "Second test", profile = Profiles.Desktop.Default)

        // First test fails: that flushes the buffer and switches to direct output
        listener.onTestStart(info)
        listener.onStepFinish(StepInfo("1", "step"), StepResult.Failed(IllegalStateException("boom")))
        listener.onTestFinish(info, TestResult.Failed(IllegalStateException("boom")))
        val afterFirst = lines.size

        listener.onTestStart(second)
        listener.onStepStart(StepInfo(label = "1", description = "step of the second test"))

        assertEquals(
            afterFirst,
            lines.size,
            "The second test must be buffered again - a failure in an earlier test " +
                "must not disable buffering for good: ${lines.drop(afterFirst)}",
        )
    }

    @Test
    fun operationsAreReportedOnlyWhenVerbose() {
        val quiet = mutableListOf<String>()
        val loud = mutableListOf<String>()
        val operation = OperationInfo(operation = "click", node = "tag 'CARD'")

        val result = OperationResult.Succeeded(attempts = 1, duration = 5.milliseconds)
        ConsoleListener(streaming = true, out = { line -> quiet += line })
            .onOperationFinish(operation, result)
        ConsoleListener(verbose = true, streaming = true, out = { line -> loud += line })
            .onOperationFinish(operation, result)

        assertTrue(quiet.isEmpty(), "Operations are noise unless asked for: $quiet")
        assertTrue(loud.any { line -> line.contains("click") }, "$loud")
    }

    @Test
    fun aFailedOperationIsMarkedAsSuchInVerboseOutput() {
        val lines = mutableListOf<String>()
        val listener = ConsoleListener(verbose = true, streaming = true, out = { line -> lines += line })

        listener.onOperationFinish(
            OperationInfo(operation = "assertIsDisplayed", node = "tag 'CARD'"),
            OperationResult.Failed(AssertionError("not displayed"), attempts = 12, duration = 5.milliseconds),
        )

        assertTrue(
            lines.single().contains("FAILED"),
            "A failed operation must be distinguishable from a successful one: $lines",
        )
    }

    @Test
    fun attemptsAreShownOnlyWhenThereWasAWait() {
        val instant = mutableListOf<String>()
        val awaited = mutableListOf<String>()
        val operation = OperationInfo(operation = "assertIsDisplayed", node = "tag 'CARD'")

        ConsoleListener(verbose = true, streaming = true, out = { line -> instant += line })
            .onOperationFinish(operation, OperationResult.Succeeded(attempts = 1, duration = 1.milliseconds))
        ConsoleListener(verbose = true, streaming = true, out = { line -> awaited += line })
            .onOperationFinish(operation, OperationResult.Succeeded(attempts = 40, duration = 1.milliseconds))

        assertTrue(
            !instant.single().contains("attempt"),
            "One attempt is the normal case - saying so is noise: $instant",
        )
        assertTrue(awaited.single().contains("40 attempts"), "A real wait must be visible: $awaited")
    }
}
