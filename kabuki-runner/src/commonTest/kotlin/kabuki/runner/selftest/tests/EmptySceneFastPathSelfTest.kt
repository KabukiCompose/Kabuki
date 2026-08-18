package kabuki.runner.selftest.tests

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import kabuki.KabukiComposeContext
import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.KabukiAssertionError
import kabuki.KabukiUsageError
import kabuki.defaultTestProfile
import kabuki.listener.TestInfo
import kabuki.listener.TestResult
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-test for the fast path: once the test has installed content itself, an
 * empty scene is a verdict, not a wait.
 *
 * Only a misconfigured APK shows this on a device, so the scene is faked: what
 * matters is how many attempts retry spends, and a fake can count them.
 */
class EmptySceneFastPathSelfTest {

    @Test
    fun contentInstalledStopsAtTheFirstAttempt() {
        onDeadScene { scene ->
            setContent { }
            assertFailsWith<KabukiUsageError> {
                node(SelfTestTags.COUNTER_BUTTON).assertExists()
            }

            assertEquals(1, scene.attempts, "The empty scene was retried instead of being ruled on")
        }
    }

    @Test
    fun aProbeDoesNotAnswerNoOnABrokenScene() {
        onDeadScene {
            setContent { }
            // `passed` swallows AssertionError and answers false. A broken scene is
            // not an answer, and "no" here would be a lie.
            assertFailsWith<KabukiUsageError> {
                node(SelfTestTags.COUNTER_BUTTON).passed { assertDoesNotExist() }
            }
        }
    }

    @Test
    fun aFailedAssertionKeepsItsOwnMessage() {
        val scene = WordyScene()
        val scope = KabukiTestScope(context = scene, config = KabukiConfig(), profile = defaultTestProfile())
        val info = TestInfo(name = "wordy scene", profile = scope.profile)
        scope.notifyTestStart(info)
        try {
            scope.setContent { }
            val error = assertFailsWith<KabukiAssertionError> {
                scope.node(SelfTestTags.COUNTER_BUTTON).assertExists()
            }

            // Rewriting this into an empty-scene verdict would steal a failure that
            // is about the screen's text, not about the scene.
            assertTrue(
                "Nothing is composed on screen" !in error.message.orEmpty(),
                "A failed assertion was rewritten as an empty scene: ${error.message}",
            )
        } finally {
            scope.notifyTestFinish(info, TestResult.Passed)
        }
    }

    @Test
    fun withoutSetContentTheWaitIsSpentInFull() {
        onDeadScene { scene ->
            // No setContent: content may still be on its way (interop over a foreign
            // rule), so retry has to give it the whole timeout before ruling.
            assertFailsWith<KabukiUsageError> {
                node(SelfTestTags.COUNTER_BUTTON).assertExists()
            }

            assertEquals(ATTEMPT_BUDGET, scene.attempts, "The wait was cut short")
        }
    }
}

/**
 * A scene that is alive but shows Compose's own wording on screen. Nothing to do
 * with an empty hierarchy - the words just happen to be the ones we look for.
 */
private class WordyScene : KabukiComposeContext by DeadScene() {
    override fun onNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteraction {
        throw AssertionError("Failed: assertTextContains. Actual text: 'No compose hierarchies found in the app'")
    }
}

/**
 * Runs [block] on a scope over a scene that composed nothing.
 *
 * Finishes the scope no matter what: a KabukiTestScope binds itself to the THREAD,
 * and one left bound makes the next test believe a Kabuki test is running. Two
 * unrelated self-tests failed exactly that way.
 */
private fun onDeadScene(block: KabukiTestScope.(scene: DeadScene) -> Unit) {
    val scene = DeadScene()
    val scope = KabukiTestScope(context = scene, config = KabukiConfig(), profile = defaultTestProfile())
    val info = TestInfo(name = "dead scene", profile = scope.profile)
    scope.notifyTestStart(info)
    try {
        scope.block(scene)
    } finally {
        scope.notifyTestFinish(info, TestResult.Passed)
    }
}

private const val ATTEMPT_BUDGET = 20

/** A scene that composed nothing: every lookup fails the way Compose fails it. */
private class DeadScene : KabukiComposeContext {

    var attempts = 0
        private set

    override fun onNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteraction {
        throw IllegalStateException(EMPTY_SCENE_MESSAGE)
    }

    override fun onAllNodes(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteractionCollection {
        throw IllegalStateException(EMPTY_SCENE_MESSAGE)
    }

    // Attempts instead of milliseconds: the budget is what the test reads, and a
    // clock would make it a timing test.
    override fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean) {
        while (attempts < ATTEMPT_BUDGET) {
            attempts++
            if (condition()) {
                return
            }
        }
        throw ComposeTimeoutException("Condition '$conditionDescription' not met")
    }

    override fun waitForIdle() = Unit

    override fun setContent(content: @Composable () -> Unit) = Unit

    private companion object {
        const val EMPTY_SCENE_MESSAGE = "No compose hierarchies found in the app. Possible reasons include: ..."
    }
}
