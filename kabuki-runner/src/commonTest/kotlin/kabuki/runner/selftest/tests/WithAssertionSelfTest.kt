package kabuki.runner.selftest.tests

import kabuki.KabukiAssertionError
import kabuki.listener.KabukiListener
import kabuki.listener.OperationInfo
import kabuki.listener.OperationResult
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for [kabuki.page.UiNode.withAssertion]: the operation counts as done
 * only when its effect appears, and repeats until then.
 */
class WithAssertionSelfTest : SelfTestCase() {

    @Test
    fun theOperationRepeatsUntilItsEffectAppears() {
        val recorder = AssertionRecorder()
        runTest(name = "click until effect", config = { listeners += recorder }) {
            var effects = 0
            node(SelfTestTags.COUNTER_BUTTON)
                .withAssertion("the counter reaches 3") {
                    effects++
                    node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 3")
                }
                .click()

            assertTrue(effects >= 3, "The assertion must run on every attempt, ran $effects times")
        }

        assertTrue(
            recorder.succeeded.any { it.startsWith("click until") },
            "Operations seen: ${recorder.succeeded}",
        )
    }

    @Test
    fun theFailureNamesTheConditionAndNotJustTheOperation() {
        runTest(name = "condition never holds") {
            val error = assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.COUNTER_BUTTON)
                    .withTimeout(300.milliseconds)
                    .withAssertion("a dialog that never opens") {
                        node(SelfTestTags.DELAYED_BLOCK).assertTextEquals("never")
                    }
                    .click()
            }

            // Without the condition in the message this reads as "the click failed",
            // sending the reader to look at the wrong thing.
            assertTrue(
                "until 'a dialog that never opens'" in error.message.orEmpty(),
                "Got: ${error.message}",
            )
        }
    }

    @Test
    fun clickUntilIsTheSameThingWithAShorterName() {
        val recorder = AssertionRecorder()
        runTest(name = "clickUntil", config = { listeners += recorder }) {
            node(SelfTestTags.COUNTER_BUTTON).clickUntil("the counter reaches 2") {
                node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 2")
            }
        }

        assertTrue(
            recorder.succeeded.any { it == "click until 'the counter reaches 2'" },
            "Operations seen: ${recorder.succeeded}",
        )
    }

    @Test
    fun anOperationWithoutAConditionIsUnchanged() {
        val recorder = AssertionRecorder()
        runTest(name = "no condition", config = { listeners += recorder }) { app ->
            node(SelfTestTags.COUNTER_BUTTON).click()

            assertEquals(1, app.counter)
        }

        assertTrue(recorder.succeeded.contains("click"), "Operations seen: ${recorder.succeeded}")
    }

    @Test
    fun theConditionIsNotCarriedOverToOtherNodes() {
        runTest(name = "condition stays on its node") { app ->
            val guarded = node(SelfTestTags.COUNTER_BUTTON)
                .withAssertion("counter reaches 1") {
                    node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 1")
                }
            guarded.click()

            // withAssertion returns a COPY: the original node must stay plain, or a
            // condition would silently leak into every later use of it.
            node(SelfTestTags.LOAD_BUTTON).click()
            assertEquals(1, app.counter)
        }
    }
}

/** Records the names of operations that succeeded. */
private class AssertionRecorder : KabukiListener {
    val succeeded = mutableListOf<String>()

    override fun onOperationFinish(operation: OperationInfo, result: OperationResult) {
        if (result is OperationResult.Succeeded) {
            succeeded += operation.operation
        }
    }
}
