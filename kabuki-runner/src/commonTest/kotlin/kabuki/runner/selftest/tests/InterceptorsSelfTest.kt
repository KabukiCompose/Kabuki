package kabuki.runner.selftest.tests

import kabuki.ClickViaSemanticsAction
import kabuki.KabukiInterceptor
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Self-test: operation interceptors.
 *
 * They exist so the public API can stay free of variants. Instead of `click`
 * carrying a strategy parameter, a project that hits a platform quirk installs
 * an interceptor once - see [ClickViaSemanticsAction].
 *
 * The desktop-only `ClickOnUiThread` is covered by DesktopInterceptorsSelfTest.
 */
class InterceptorsSelfTest : SelfTestCase() {

    @Test
    fun builtInClickInterceptorsReplaceTheClick() = runTest(
        name = "Built-in click interceptors",
        config = { interceptors += ClickViaSemanticsAction() },
    ) { app ->
        step("A click goes through the semantics action, not a pointer event") {
            node(SelfTestTags.COUNTER_BUTTON).click()
            node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 1")
            assertEquals(1, app.counter)
        }
    }

    @Test
    fun chainRunsInOrderAndSeesEveryOperation() {
        val seen = mutableListOf<String>()
        val recorder = KabukiInterceptor { operation ->
            seen += operation.name
            operation.proceed()
        }

        runTest(name = "Interceptor chain", config = { interceptors += recorder }) {
            step("Two operations pass through the interceptor") {
                node(SelfTestTags.COUNTER_BUTTON).click()
                node(SelfTestTags.COUNTER_VALUE).assertIsDisplayed()
            }
        }

        // Operations are also retried, so an entry can repeat - what matters is
        // that the interceptor saw both, in order of execution.
        assertEquals("click", seen.first(), "The click must be intercepted first: $seen")
        assertEquals(
            true,
            seen.contains("assertIsDisplayed"),
            "The assertion must be intercepted too: $seen",
        )
    }

    @Test
    fun interceptorCanSkipTheOriginalOperation() {
        var skipped = 0
        val swallowClicks = KabukiInterceptor { operation ->
            if (operation.name == "click") {
                skipped++          // deliberately does NOT call proceed()
            } else {
                operation.proceed()
            }
        }

        runTest(name = "Interceptor overrides the operation", config = { interceptors += swallowClicks }) { app ->
            step("The click is swallowed, so the counter stays at zero") {
                node(SelfTestTags.COUNTER_BUTTON).click()
                assertEquals(0, app.counter, "The interceptor did not pass the click through")
            }
        }

        assertEquals(1, skipped, "The interceptor should have swallowed exactly one click")
    }
}
