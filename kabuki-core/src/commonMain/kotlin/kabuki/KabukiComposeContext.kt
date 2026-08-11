package kabuki

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider

/**
 * Everything Kabuki needs from a Compose test scene. Implemented by adapters
 * over ComposeUiTest (Kabuki's own runners) and over ComposeTestRule
 * (kabuki-junit4 - incremental adoption in existing projects with
 * their own rules and activities).
 *
 * Extends [SemanticsNodeInteractionsProvider], so onNode/onAllNodes and every
 * standard finder extension work on it directly.
 */
public interface KabukiComposeContext : SemanticsNodeInteractionsProvider {

    /** Retry primitive: advances the scene until the condition holds or times out. */
    public fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean)

    /** Waits until composition, layout and pending effects have settled. */
    public fun waitForIdle()

    /**
     * Installs content into the scene. Host-managed scenes (an existing
     * activity behind a ComposeTestRule) may not support this - adapters throw
     * IllegalStateException with a hint in that case.
     */
    public fun setContent(content: @Composable () -> Unit)
}

/** Adapter over Kabuki's own runner scene. */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.asKabukiContext(): KabukiComposeContext {
    val test = this
    return object : KabukiComposeContext, SemanticsNodeInteractionsProvider by test {
        override fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean) {
            test.waitUntil(conditionDescription, timeoutMillis, condition)
        }

        override fun waitForIdle() {
            test.waitForIdle()
        }

        override fun setContent(content: @Composable () -> Unit) {
            test.setContent(content)
        }
    }
}
