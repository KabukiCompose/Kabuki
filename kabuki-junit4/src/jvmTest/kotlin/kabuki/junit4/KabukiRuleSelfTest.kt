package kabuki.junit4

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kabuki.listener.KabukiListener
import kabuki.listener.StepInfo
import kabuki.listener.TestInfo
import kabuki.listener.TestResult
import kabuki.page.Screen
import kabuki.KabukiUsageError
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Self-test for [KabukiRule] - the interop form that knows where the test begins
 * and ends, and is therefore the only one that can name the test, report it and
 * release the page objects afterwards.
 *
 * The finish event arrives AFTER the test body, so it cannot be asserted from
 * inside one. [ReportedTest] is therefore run through JUnit itself and its events
 * are inspected afterwards.
 */
class KabukiRuleSelfTest : KabukiInterop {

    private val events = EventRecorder()

    // Inside the Compose rule on purpose: the scene must still be alive when the
    // test-finished event fires.
    @get:Rule(order = 0)
    val composeRule = createComposeRule()

    @get:Rule(order = 1)
    val kabukiRule = KabukiRule(composeRule, config = { listeners += events })

    override val kabukiScope get() = kabukiRule.kabukiScope

    @Test
    fun theRuleNamesTheTestAfterTheJunitMethod() {
        composeRule.setContent { MiniRuleApp() }

        // Flat call through the mixin - what a migrated base class gives its tests.
        onScreen<RuleScreen> { text.assertTextContains("Hello") }

        // The name comes from JUnit, so no test has to repeat its own name.
        assertEquals(listOf("theRuleNamesTheTestAfterTheJunitMethod"), events.started)
    }

    @Test
    fun oneScopeServesTheWholeTest() {
        composeRule.setContent { MiniRuleApp() }

        kabukiScope.step("first") { onScreen<RuleScreen> { text.assertExists() } }
        kabukiScope.step("second") { onScreen<RuleScreen> { text.assertExists() } }

        // Sequential numbering only happens if both steps went through the SAME
        // scope - a rule that rebuilt it per call would restart at 1.
        assertEquals(listOf("1", "2"), events.stepLabels)
    }

    @Test
    fun theShortScreenFormWorksOverAForeignRule() {
        composeRule.setContent { MiniRuleApp() }

        // No onScreen: the object screen asks the thread which test is running,
        // and under a rule there IS one.
        RuleObjectScreen { text.assertTextContains("Hello") }
    }

    @Test
    fun anInnerScopeGivesTheOuterOneBack() {
        composeRule.setContent { MiniRuleApp() }
        val outer = kabukiScope

        // A nested scope over the same rule - the block form inside a rule-driven
        // test. When it ends it must restore the outer test, not clear it.
        composeRule.kabuki(name = "inner") { node("rule_text").assertExists() }

        assertTrue(kabukiScope === outer, "The rule's scope must still be the current one")
        // Would throw "needs a running Kabuki test" if the inner scope had cleared it.
        RuleObjectScreen { text.assertExists() }
    }

    @Test
    fun theEndOfAPassingTestIsReported() {
        val recorder = EventRecorder()
        val rule = KabukiRule(composeRule, config = { listeners += recorder })

        // The rule is driven directly: the finish event lands after the test body,
        // so it cannot be observed from inside a running test.
        rule.apply(statementOf { }, descriptionOf("passes")).evaluate()

        assertEquals(listOf("passes" to true), recorder.finished)
    }

    @Test
    fun aFailingTestIsReportedAndTheFailureSurvives() {
        val recorder = EventRecorder()
        val rule = KabukiRule(composeRule, config = { listeners += recorder })
        val boom = AssertionError("deliberate")

        val thrown = assertFailsWith<AssertionError> {
            rule.apply(statementOf { throw boom }, descriptionOf("fails")).evaluate()
        }

        // Reported as failed AND rethrown: a listener must never swallow the test.
        assertEquals(listOf("fails" to false), recorder.finished)
        assertTrue(thrown === boom, "The original failure must reach JUnit unchanged")
    }

    @Test
    fun theScopeIsGoneOutsideATest() {
        val rule = KabukiRule(composeRule)

        // Nothing ran through this rule, so there is no test to belong to.
        assertFailsWith<KabukiUsageError> { rule.kabukiScope }
    }

    @Test
    fun theScopeIsReleasedWhenTheTestEnds() {
        val rule = KabukiRule(composeRule)
        rule.apply(statementOf { }, descriptionOf("done")).evaluate()

        // A rule instance survives its test; a leftover scope would let the next
        // one act on a scene that is already gone.
        assertFailsWith<KabukiUsageError> { rule.kabukiScope }
    }
}

private fun statementOf(body: () -> Unit): Statement {
    return object : Statement() {
        override fun evaluate() {
            body()
        }
    }
}

private fun descriptionOf(method: String): Description {
    return Description.createTestDescription("KabukiRuleSelfTest", method)
}

@Composable
private fun MiniRuleApp() {
    Column(modifier = Modifier.testTag("rule_screen")) {
        BasicText(text = "Hello", modifier = Modifier.testTag("rule_text"))
    }
}

/** Records what the rule is expected to report. */
class EventRecorder : KabukiListener {
    val started: MutableList<String> = mutableListOf()
    val finished: MutableList<Pair<String, Boolean>> = mutableListOf()
    val stepLabels: MutableList<String> = mutableListOf()

    override fun onTestStart(test: TestInfo) {
        started += test.name
    }

    override fun onTestFinish(test: TestInfo, result: TestResult) {
        finished += test.name to (result is TestResult.Passed)
    }

    override fun onStepStart(step: StepInfo) {
        stepLabels += step.label
    }
}

private class RuleScreen : Screen<RuleScreen>() {
    override val root = node { withTag("rule_screen") }
    val text = node { withTag("rule_text") }
}

/** Same screen as an object - for the short form, which needs no reflection. */
private object RuleObjectScreen : Screen<RuleObjectScreen>() {
    override val root = node { withTag("rule_screen") }
    val text = node { withTag("rule_text") }
}
