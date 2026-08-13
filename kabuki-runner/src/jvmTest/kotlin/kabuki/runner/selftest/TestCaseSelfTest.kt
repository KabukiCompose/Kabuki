package kabuki.runner.selftest

import androidx.compose.runtime.Composable
import kabuki.KabukiConfig
import kabuki.runner.KabukiTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-test for [KabukiTestCase] - the optional base class. It was shipped in the
 * MVP and had no test at all: every promise in its documentation (a fresh
 * environment per test, hooks around the body, class-level config) was unverified.
 */
class TestCaseSelfTest : KabukiTestCase<CountingEnvironment>() {

    override fun createEnvironment(): CountingEnvironment {
        return CountingEnvironment()
    }

    override fun KabukiConfig.configure() {
        // Class-level config: every test of this class inherits it.
        maxTreeDumpLines = 7
    }

    @Composable
    override fun Content(environment: CountingEnvironment) {
        SelfTestApp(environment.state)
    }

    override fun beforeTest(environment: CountingEnvironment) {
        environment.events += "before"
    }

    override fun afterTest(environment: CountingEnvironment) {
        environment.events += "after"
        finishedEnvironments += environment
    }

    @Test
    fun theContentIsInstalledAndTheHooksRunAroundTheBody() {
        runTest(name = "base class lifecycle") { environment ->
            environment.events += "body"

            // The content declared once by the class is on screen already - the test
            // never calls setContent itself.
            node(SelfTestTags.TITLE).assertTextContains("Kabuki SelfTest")
            assertEquals(listOf("before", "body"), environment.events)
        }
    }

    @Test
    fun theEnvironmentIsBuiltFreshForEveryTest() {
        runTest(name = "fresh environment 1") { environment ->
            node(SelfTestTags.COUNTER_BUTTON).click()
            environment.state.counter.let { assertEquals(1, it) }
        }
        runTest(name = "fresh environment 2") { environment ->
            // A shared environment would still hold the click from the test above.
            assertEquals(0, environment.state.counter, "Every test must get a new environment")
        }
    }

    @Test
    fun theAfterHookRunsEvenWhenTheTestFails() {
        val before = finishedEnvironments.size

        assertFailsWith<AssertionError> {
            runTest(name = "failing body") { environment ->
                environment.events += "body"
                throw AssertionError("deliberate")
            }
        }

        assertEquals(before + 1, finishedEnvironments.size, "afterTest must run for a failed test too")
        assertTrue(finishedEnvironments.last().events.contains("after"))
    }

    @Test
    fun classLevelConfigAppliesAndPerTestConfigWinsOverIt() {
        runTest(name = "class config") { _ ->
            assertEquals(7, config.maxTreeDumpLines, "The class-level configure() must apply")
        }
        runTest(name = "per-test config", config = { maxTreeDumpLines = 3 }) { _ ->
            assertEquals(3, config.maxTreeDumpLines, "Per-test config must be applied on top")
        }
    }

    private companion object {
        /** Filled by afterTest - the only way to observe a hook that runs after the body. */
        val finishedEnvironments = mutableListOf<CountingEnvironment>()
    }
}

/** Environment of the test class: app state plus a log of the hooks that fired. */
class CountingEnvironment {
    val state: SelfTestAppState = SelfTestAppState()
    val events: MutableList<String> = mutableListOf()
}
