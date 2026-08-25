package kabuki.junit4

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import kabuki.KabukiComposeContext
import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.TestProfile
import kabuki.listener.TestInfo
import kabuki.listener.TestResult
import kabuki.page.Screen
import kabuki.page.onScreen

/**
 * Incremental adoption: Kabuki on top of an EXISTING ComposeTestRule - your
 * activity, your DI, your base test class. Old framework calls and Kabuki
 * calls coexist in one test over one rule; screens migrate one at a time.
 *
 * Note: with a host-managed rule the [TestProfile] describes the environment
 * for os()/layout() forks - it does not control the scene size (the host does).
 */
public fun ComposeTestRule.asKabukiContext(): KabukiComposeContext {
    val rule = this
    return object : KabukiComposeContext, SemanticsNodeInteractionsProvider by rule {
        override fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean) {
            if (conditionDescription != null) {
                rule.waitUntil(conditionDescription, timeoutMillis, condition)
            } else {
                rule.waitUntil(timeoutMillis, condition)
            }
        }

        override fun waitForIdle() {
            rule.waitForIdle()
        }

        override fun setContent(content: @Composable () -> Unit) {
            val contentRule = rule as? ComposeContentTestRule ?: error(
                "This ComposeTestRule does not accept content - it is managed by the host " +
                    "(e.g. createAndroidComposeRule<YourActivity>() already shows your app). " +
                    "Drive the existing UI instead of calling setContent.",
            )
            contentRule.setContent(content)
        }
    }
}

/**
 * A reusable Kabuki scope over the rule - hold it in your base test class:
 *
 * ```kotlin
 * abstract class BaseTest : KabukiInterop {
 *     @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
 *     override val kabukiScope by lazy { composeRule.kabukiScope() }
 * }
 * ```
 *
 * A bare scope has no idea when the test ends, so listeners get steps and
 * operations but no test events, and page objects stay bound until the next
 * scope on this thread replaces them. For a base test class prefer [KabukiRule],
 * which JUnit tells where the test starts and ends; for a single block, [kabuki].
 */
public fun ComposeTestRule.kabukiScope(
    profile: TestProfile = defaultInteropProfile(),
    config: KabukiConfig.() -> Unit = {},
): KabukiTestScope {
    return KabukiTestScope(
        context = asKabukiContext(),
        config = KabukiConfig().apply(config),
        profile = profile,
    )
}

/**
 * Block form for a series of Kabuki calls with steps and scenarios:
 *
 * ```kotlin
 * composeRule.kabuki(name = "Payment with a saved card") {
 *     step("New compose part") { onScreen<PaymentScreen> { payButton.click() } }
 * }
 * ```
 *
 * Reports the test to listeners (a reporter sees a test, not loose steps) and
 * releases the page objects at the end. For a whole test class [KabukiRule] does
 * the same through JUnit; a bare [kabukiScope] never learns when the test is over.
 */
public fun ComposeTestRule.kabuki(
    name: String = "kabuki interop",
    profile: TestProfile = defaultInteropProfile(),
    config: KabukiConfig.() -> Unit = {},
    block: KabukiTestScope.() -> Unit,
) {
    val scope = kabukiScope(profile, config)
    val info = TestInfo(name = name, profile = scope.profile)
    scope.notifyTestStart(info)
    var result: TestResult = TestResult.Passed
    try {
        scope.block()
    } catch (e: Throwable) {
        result = TestResult.Failed(e)
        throw e
    } finally {
        // One place, so a reporter sees one finished test.
        scope.notifyTestFinish(info, result)
    }
}

/**
 * One-shot flat form - a Kabuki screen straight on the rule:
 *
 * ```kotlin
 * composeRule.onScreen<PaymentScreen> { payButton.click() }
 * ```
 */
public inline fun <reified T : Screen<T>> ComposeTestRule.onScreen(
    noinline block: T.() -> Unit = {},
): T {
    return kabukiScope().onScreen(block)
}

/**
 * Mixin for base test classes: implement it once and call `onScreen<KabukiScreen>`
 * FLAT in tests, next to legacy framework calls. The compiler picks the framework
 * by the screen type (Kabuki screens resolve here; legacy screens resolve to the
 * legacy top-level functions).
 */
public interface KabukiInterop {
    /** The Kabuki scope of the current test, usually built from the existing ComposeTestRule. */
    public val kabukiScope: KabukiTestScope
}

/** Kabuki's `onScreen` for classes implementing [KabukiInterop]. */
public inline fun <reified T : Screen<T>> KabukiInterop.onScreen(
    noinline block: T.() -> Unit = {},
): T {
    return kabukiScope.onScreen(block)
}

/** Platform default profile for interop scopes (desktop defaults / real device). */
public expect fun defaultInteropProfile(): TestProfile
