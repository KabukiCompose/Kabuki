package kabuki.runner.selftest

import kabuki.listener.KabukiListener
import kabuki.listener.StepInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-test for listener isolation.
 *
 * A listener observes a test; it must not decide its outcome. Before this
 * existed, a reporter that could not write its file turned a perfectly good
 * test red, with its own IOException standing in for the real result.
 */
class ListenerIsolationSelfTest : SelfTestCase() {

    @Test
    fun aBrokenListenerDoesNotFailTheTest() {
        val healthy = StepRecordingListener()

        runTest(
            name = "broken listener",
            config = {
                listeners += ThrowingListener()
                listeners += healthy
            },
        ) {
            step("The step runs even though a listener blew up") {
                node(SelfTestTags.COUNTER_BUTTON).click()
                node(SelfTestTags.COUNTER_VALUE).assertTextContains("1")
            }
        }

        assertEquals(
            listOf("1"),
            healthy.labels,
            "One listener throwing must not stop the others from being notified",
        )
    }

    @Test
    fun strictListenersMakeListenerFailuresVisible() {
        val failure = assertFailsWith<IllegalStateException> {
            runTest(
                name = "strict listeners",
                config = {
                    strictListeners = true
                    listeners += ThrowingListener()
                },
            ) {
                step("This step never completes") {
                    node(SelfTestTags.COUNTER_BUTTON).click()
                }
            }
        }

        assertTrue(
            failure.message?.contains("listener is broken") == true,
            "The listener's own failure must be what surfaces, got: ${failure.message}",
        )
    }
}

/** Stands in for a reporter that cannot do its job - a full disk, a closed socket. */
private class ThrowingListener : KabukiListener {
    override fun onStepStart(step: StepInfo) {
        error("listener is broken")
    }
}

private class StepRecordingListener : KabukiListener {
    val labels: MutableList<String> = mutableListOf()

    override fun onStepStart(step: StepInfo) {
        labels += step.label
    }
}
