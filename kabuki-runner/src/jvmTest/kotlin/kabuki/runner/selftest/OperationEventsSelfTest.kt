package kabuki.runner.selftest

import kabuki.KabukiAssertionError
import kabuki.KabukiListener
import kabuki.OperationInfo
import kabuki.OperationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for operation events. The retry loop is the only place that knows the
 * attempt count and the duration, so without these events neither is measurable.
 */
class OperationEventsSelfTest : SelfTestCase() {

    @Test
    fun everyOperationIsReportedFromStartToFinish() {
        val recorder = OperationRecorder()

        runTest(name = "operation events", config = { listeners += recorder }) {
            node(SelfTestTags.COUNTER_BUTTON).click()
        }

        assertTrue(recorder.started.any { it.operation == "click" }, "start not reported: ${recorder.started}")
        val finish = recorder.finished.single { (info, _) -> info.operation == "click" }.second
        assertTrue(finish is OperationResult.Succeeded, "click should have succeeded: $finish")
        assertTrue(
            finish.duration > Duration.ZERO,
            "A successful operation must carry its duration too, got ${finish.duration}",
        )
    }

    @Test
    fun attemptsShowWhetherTheUiMadeUsWait() {
        val recorder = OperationRecorder()

        runTest(name = "attempts", config = { listeners += recorder }) {
            step("Instant assertion - one attempt") {
                node(SelfTestTags.TITLE).assertIsDisplayed()
            }
            step("Block that appears after a delay - many attempts") {
                node(SelfTestTags.DELAYED_BLOCK).assertIsDisplayed()
            }
        }

        val instant = recorder.resultFor("assertIsDisplayed", nodeContains = "TITLE")
        val awaited = recorder.resultFor("assertIsDisplayed", nodeContains = "DELAYED_BLOCK")

        assertEquals(1, instant.attempts, "An already displayed node must not need a retry")
        assertTrue(
            awaited.attempts > 1,
            "A node that appears later must take several attempts, got ${awaited.attempts}",
        )
    }

    @Test
    fun failureIsReportedWithTheErrorAndTheNumbers() {
        val recorder = OperationRecorder()

        assertFailsWith<KabukiAssertionError> {
            runTest(name = "failing operation", config = { listeners += recorder }) {
                node("selftest_absent").withTimeout(200.milliseconds).assertIsDisplayed()
            }
        }

        val result = recorder.finished.map { it.second }.filterIsInstance<OperationResult.Failed>().single()
        assertTrue(result.error is KabukiAssertionError, "The listener must see the real error: ${result.error}")
        assertTrue(result.attempts >= 1, "Even a doomed operation runs at least once")
        assertTrue(result.duration > Duration.ZERO, "Duration must be measured, got ${result.duration}")
    }

    @Test
    fun operationsAreReportedUnderTheirPublicNames() {
        val recorder = OperationRecorder()

        runTest(name = "operation names", config = { listeners += recorder }) {
            node(SelfTestTags.COUNTER_VALUE).assertTextContains("0")
            node(SelfTestTags.TITLE).assertTextEquals("Kabuki SelfTest")
        }

        val names = recorder.started.map { it.operation }

        // A report naming a PRIVATE helper sends the reader looking for a line
        // that does not exist in the test.
        assertTrue(
            names.any { it.startsWith("assertTextContains") },
            "Expected the public name, got: $names",
        )
        assertTrue(
            names.any { it.startsWith("assertTextEquals") },
            "Expected the public name, got: $names",
        )
        assertTrue(
            names.none { it.startsWith("assertText(") },
            "The private helper name must never reach a report: $names",
        )
    }

    @Test
    fun everyStartIsMatchedByAFinishEvenIfAListenerIsBroken() {
        val recorder = OperationRecorder()

        runTest(
            name = "symmetry",
            config = {
                listeners += ThrowingOnStart()
                listeners += recorder
            },
        ) {
            node(SelfTestTags.COUNTER_BUTTON).click()
            node(SelfTestTags.COUNTER_VALUE).assertTextContains("1")
        }

        // Symmetry alone would also hold for zero events, which is why the count
        // is checked first: a reporter opening a span on start must never be left
        // with a dangling one, but silence is not the answer either.
        assertEquals(2, recorder.started.size, "Both operations must be reported: ${recorder.started}")
        assertEquals(
            recorder.started.size,
            recorder.finished.size,
            "Every start needs its finish: started=${recorder.started.size} finished=${recorder.finished.size}",
        )
    }

    @Test
    fun collectionsAndListsAreReportedToo() {
        val recorder = OperationRecorder()

        runTest(name = "collection events", config = { listeners += recorder }) {
            nodeAll(SelfTestTags.LAZY_ITEM).assertCountAtLeast(1)
        }

        assertTrue(
            recorder.started.any { it.operation.startsWith("assertCountAtLeast") },
            "Collection assertions must be reported like node ones, got: ${recorder.started}",
        )
    }
}

/** Stands for a listener that is broken from the very first event. */
private class ThrowingOnStart : KabukiListener {
    override fun onOperationStart(operation: OperationInfo) {
        error("listener exploded on start")
    }
}

/** Collects everything the SPI reports about operations. */
private class OperationRecorder : KabukiListener {
    val started: MutableList<OperationInfo> = mutableListOf()
    val finished: MutableList<Pair<OperationInfo, OperationResult>> = mutableListOf()

    override fun onOperationStart(operation: OperationInfo) {
        started += operation
    }

    override fun onOperationFinish(operation: OperationInfo, result: OperationResult) {
        finished += operation to result
    }

    fun resultFor(operation: String, nodeContains: String): OperationResult {
        return finished
            .single { (info, _) -> info.operation == operation && nodeContains in info.node }
            .second
    }
}
