package kabuki.junit4

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kabuki.KabukiAssertionError
import kabuki.listener.KabukiListener
import kabuki.listener.TestInfo
import kabuki.listener.TestResult
import kabuki.page.Screen
import kabuki.semantics.testTag
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Rule
import org.junit.Test

/**
 * Self-test of incremental adoption: Kabuki drives a scene owned by a FOREIGN
 * junit4 ComposeTestRule - exactly how a large project adopts Kabuki without
 * dropping its existing base classes. Exercises all three API forms.
 *
 * Tags here are plain strings ON PURPOSE. That is what a project that has not
 * migrated yet looks like: its production code is tagged the way Espresso-era
 * tooling requires, and interop exists precisely for such projects. [MiniTags]
 * covers the other half - production code already moved to kabuki-semantics enum
 * tags while the tests still run on someone else's rule, which is the usual
 * order of migration.
 */
class InteropSelfTest : KabukiInterop {

    @get:Rule
    val composeRule = createComposeRule()

    // The mixin field: one line in a base class, flat onScreen<> in every test
    override val kabukiScope by lazy { composeRule.kabukiScope() }

    @Composable
    private fun MiniApp() {
        var clicks by remember { mutableStateOf(0) }
        Column(modifier = Modifier.testTag("mini_screen")) {
            BasicText(
                text = "Clicks: $clicks",
                modifier = Modifier.testTag("mini_counter"),
            )
            BasicText(
                text = "Click me",
                modifier = Modifier
                    .testTag("mini_button")
                    .clickable { clicks++ },
            )
            // The already-migrated part of the screen: an enum tag from
            // kabuki-semantics next to the legacy string ones.
            BasicText(
                text = "Beta",
                modifier = Modifier.testTag(MiniTags.BADGE),
            )
        }
    }

    @Test
    fun blockFormOverForeignRule() {
        composeRule.setContent { MiniApp() }

        composeRule.kabuki {
            step("Click via the block form") {
                node("mini_button").click()
                node("mini_counter").assertTextContains("Clicks: 1")
            }
        }

        // Legacy-style raw rule calls still work side by side
        composeRule.onNodeWithTag("mini_screen").assertExists()
    }

    @Test
    fun flatOnScreenOnRule() {
        composeRule.setContent { MiniApp() }

        composeRule.onScreen<MiniScreen> {
            button.click()
            counter.assertTextContains("Clicks: 1")
        }
    }

    @Test
    fun enumTagsAreAddressableThroughTheForeignRule() {
        composeRule.setContent { MiniApp() }

        composeRule.kabuki {
            step("A half-migrated screen: enum tag next to legacy string ones") {
                node(MiniTags.BADGE).assertTextContains("Beta")
                node("mini_counter").assertTextContains("Clicks: 0")
            }
        }
    }

    @Test
    fun theBlockFormReportsAWholeTestToListeners() {
        composeRule.setContent { MiniApp() }
        val recorder = TestEventRecorder()

        composeRule.kabuki(name = "Interop with a lifecycle", config = { listeners += recorder }) {
            step("Something happens") { node("mini_button").click() }
        }

        // Without these a reporter attached in interop sees loose steps with no
        // test around them - the reason this form exists at all.
        assertEquals(listOf("Interop with a lifecycle"), recorder.started)
        assertEquals(listOf("Interop with a lifecycle" to true), recorder.finished)
    }

    @Test
    fun theBlockFormReportsAFailingTestAndRethrows() {
        composeRule.setContent { MiniApp() }
        val recorder = TestEventRecorder()

        assertFailsWith<KabukiAssertionError> {
            composeRule.kabuki(name = "Interop failure", config = { listeners += recorder }) {
                node("mini_counter").withTimeout(200.milliseconds).assertTextContains("Clicks: 99")
            }
        }

        assertEquals(listOf("Interop failure" to false), recorder.finished)
    }

    @Test
    fun flatOnScreenViaMixin() {
        composeRule.setContent { MiniApp() }

        // Flat call with no receiver - the KakaoCup migration ergonomics
        onScreen<MiniScreen> {
            button.click()
            button.click()
            counter.assertTextContains("Clicks: 2")
        }
    }
}

/** Collects the test-level events the interop block form is expected to produce. */
private class TestEventRecorder : KabukiListener {
    val started: MutableList<String> = mutableListOf()
    val finished: MutableList<Pair<String, Boolean>> = mutableListOf()

    override fun onTestStart(test: TestInfo) {
        started += test.name
    }

    override fun onTestFinish(test: TestInfo, result: TestResult) {
        finished += test.name to (result is TestResult.Passed)
    }
}

class MiniScreen : Screen<MiniScreen>() {
    override val root = node { withTag("mini_screen") }
    val button = node { withTag("mini_button") }
    val counter = node { withTag("mini_counter") }

    /** The migrated element - addressed by enum, like a fully migrated screen would be. */
    val badge = node(MiniTags.BADGE)
}

/** Stands for production code that already moved to kabuki-semantics tags. */
enum class MiniTags { BADGE }
