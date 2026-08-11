package kabuki.runner

import androidx.compose.runtime.Composable
import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.TestProfile
import kabuki.defaultTestProfile

/**
 * Optional base class for tests: the environment and content are declared once,
 * so tests do not repeat state creation and setContent. Works on every platform -
 * put it in commonTest and the same test class runs on desktop and on a device.
 *
 * [E] is the test environment (state, mocks, root components of the app). It is
 * created from scratch for every test inside [runTest] - nothing is shared
 * between tests.
 *
 * The class is not tied to any test framework: no rules or annotations, the whole
 * lifecycle lives inside [runTest]. The free [runKabukiTest] function remains an
 * equal entry point - the base class is just a convenience on top of it. An app
 * inherits its own base class and adds its own hooks (mocks, DI, data cleanup).
 */
public abstract class KabukiTestCase<E : Any> {

    /** Default environment profile for all tests of the class. */
    protected open val profile: TestProfile
        get() {
            return defaultTestProfile()
        }

    /** Default config for all tests of the class. Per-test config is applied on top. */
    protected open fun KabukiConfig.configure() {}

    /** Test environment. Invoked for every test - always a fresh instance. */
    protected abstract fun createEnvironment(): E

    /** Content under test. Installed into the test scene (and into the visible window on desktop). */
    @Composable
    protected abstract fun Content(environment: E)

    /** After the environment is created, before the content is installed: data and mock setup. */
    protected open fun beforeTest(environment: E) {}

    /** After the test, including a failed one (finally): cleanup, lifecycle dispose. */
    protected open fun afterTest(environment: E) {}

    protected fun runTest(
        name: String = this::class.simpleName ?: "Kabuki Test",
        profile: TestProfile = this.profile,
        config: KabukiConfig.() -> Unit = {},
        block: KabukiTestScope.(environment: E) -> Unit,
    ) {
        runKabukiTest(
            name = name,
            profile = profile,
            config = {
                configure()
                config()
            },
        ) {
            val environment = createEnvironment()
            beforeTest(environment)
            try {
                setContent { Content(environment) }
                block(environment)
            } finally {
                afterTest(environment)
            }
        }
    }
}
