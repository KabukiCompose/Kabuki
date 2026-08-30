package kabuki.runner.selftest.tests

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kabuki.page.Screen
import kabuki.runner.runKabukiTest
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression: several Compose scenes must survive running at the same time
 * inside ONE JVM.
 *
 * Kabuki holds no global state - the scope travels through scopeProvider - so
 * parallel tests depend entirely on Compose. Should a future desktop test scene
 * keep anything static (a scene registry, the clock, the dispatcher), parallel
 * execution stops working regardless of our own architecture.
 *
 * Threads speed the suite up several times over, while forked JVMs are
 * a net loss - each one re-initialises Skiko. Hence no maxParallelForks.
 */
class ParallelScenesProbe {

    private fun sceneWithText(text: String) {
        runKabukiTest(name = "parallel probe: $text") {
            setContent {
                Column {
                    BasicText(text = text, modifier = Modifier.testTag("probe_text"))
                }
            }
            repeat(20) {
                node("probe_text").assertTextContains(text)
            }
        }
    }

    @Test
    fun twoScenesInParallelThreads() {
        val errors = mutableListOf<Throwable>()
        val threads = listOf("alpha", "beta").map { name ->
            thread {
                try {
                    sceneWithText(name)
                } catch (e: Throwable) {
                    synchronized(errors) { errors += e }
                }
            }
        }
        threads.forEach { it.join() }

        assertTrue(
            errors.isEmpty(),
            "Parallel scenes failed:\n" + errors.joinToString("\n") { "${it::class.simpleName}: ${it.message}" },
        )
    }

    @Test
    fun parallelThreadsShareOneObjectScreen() {
        // The point of the thread-local binding: ONE singleton page object, four
        // tests, four scenes. A binding kept in a plain field would hand a thread
        // the scene of another one, and the text would not match.
        val errors = mutableListOf<Throwable>()
        val threads = (1..4).map { index ->
            thread {
                val text = "shared-$index"
                try {
                    runKabukiTest(name = "parallel object screen: $text") {
                        setContent {
                            Column {
                                BasicText(text = text, modifier = Modifier.testTag("probe_text"))
                            }
                        }
                        repeat(20) {
                            SharedProbeScreen { probeText.assertTextContains(text) }
                        }
                    }
                } catch (e: Throwable) {
                    synchronized(errors) { errors += e }
                }
            }
        }
        threads.forEach { it.join() }

        assertEquals(
            emptyList(),
            errors.map { "${it::class.simpleName}: ${it.message?.lineSequence()?.firstOrNull()}" },
            "A shared object screen must not leak between parallel tests",
        )
    }

    @Test
    fun fourScenesInParallelThreads() {
        val errors = mutableListOf<Throwable>()
        val threads = (1..4).map { index ->
            thread {
                try {
                    sceneWithText("scene-$index")
                } catch (e: Throwable) {
                    synchronized(errors) { errors += e }
                }
            }
        }
        threads.forEach { it.join() }

        assertEquals(
            emptyList(),
            errors.map { "${it::class.simpleName}: ${it.message}" },
            "Parallel scenes failed",
        )
    }
}

/** One page object for every thread - the probe for the per-thread binding. */
private object SharedProbeScreen : Screen<SharedProbeScreen>() {
    val probeText = node { withTag("probe_text") }
}
