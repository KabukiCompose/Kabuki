package kabuki.junit4

import androidx.compose.ui.test.junit4.ComposeTestRule
import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.KabukiUsageError
import kabuki.TestProfile
import kabuki.listener.TestInfo
import kabuki.listener.TestResult
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The interop form with a full test lifecycle, for base test classes:
 *
 * ```kotlin
 * abstract class BaseTest : KabukiInterop {
 *     @get:Rule(order = 0) val composeRule = createAndroidComposeRule<MainActivity>()
 *     @get:Rule(order = 1) val kabukiRule = KabukiRule(composeRule)
 *     override val kabukiScope get() = kabukiRule.kabukiScope
 * }
 *
 * class PaymentTest : BaseTest() {
 *     @Test fun pay() = onScreen<PaymentScreen> { payButton.click() }
 * }
 * ```
 *
 * What the rule adds over a bare [kabukiScope]: JUnit tells it where the test
 * begins and ends, so listeners get the test itself (a reporter sees steps inside
 * a test, not loose steps), the name comes from JUnit, and page objects are
 * released at the end instead of staying bound to a scene that is gone.
 *
 * Declare it INSIDE the Compose rule (`order = 1` against `order = 0`, or
 * `RuleChain.outerRule(composeRule).around(kabukiRule)`): the scene must be alive
 * when the finish event fires, or a listener taking a screenshot on failure finds
 * nothing there.
 */
public class KabukiRule(
    private val composeRule: ComposeTestRule,
    private val profile: TestProfile = defaultInteropProfile(),
    private val config: KabukiConfig.() -> Unit = {},
) : TestRule, KabukiInterop {

    private var currentScope: KabukiTestScope? = null

    /** The scope of the test being run. Available only inside a test method. */
    override val kabukiScope: KabukiTestScope
        get() {
            return currentScope ?: throw KabukiUsageError(
                "KabukiRule has no scope outside a test. Declare it as @get:Rule and use it " +
                    "from a @Test method - not from a field initialiser or a companion object.",
            )
        }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val scope = composeRule.kabukiScope(profile, config)
                val info = TestInfo(name = description.methodName ?: description.displayName, profile = scope.profile)
                currentScope = scope
                scope.notifyTestStart(info)
                var result: TestResult = TestResult.Passed
                try {
                    base.evaluate()
                } catch (e: Throwable) {
                    result = TestResult.Failed(e)
                    throw e
                } finally {
                    try {
                        // One place, so a reporter sees one finished test.
                        scope.notifyTestFinish(info, result)
                    } finally {
                        // Released even if reporting throws: the scope belongs to one
                        // test, and a leftover would let the next one act on a scene
                        // that is already gone.
                        currentScope = null
                    }
                }
            }
        }
    }
}
