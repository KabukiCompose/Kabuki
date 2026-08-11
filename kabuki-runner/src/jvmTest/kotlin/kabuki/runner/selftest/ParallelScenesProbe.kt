package kabuki.runner.selftest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 * parallel tests depend entirely on Compose. If a future version of the desktop
 * test scene starts keeping anything static (a scene registry, the clock, the
 * dispatcher), parallel execution stops working no matter how clean our own
 * architecture is. These tests exist to notice that immediately rather than
 * after the feature is promised to users.
 *
 * Threads speed the suite up several times over. Process-level parallelism, on
 * the other hand, is a net loss: every forked JVM re-initialises Skiko from
 * scratch, which costs more than the parallelism saves. That is why the build
 * deliberately does not set maxParallelForks.
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
