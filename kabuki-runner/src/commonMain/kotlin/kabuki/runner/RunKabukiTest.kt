package kabuki.runner

import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.TestProfile
import kabuki.defaultTestProfile

/**
 * Platform-agnostic entry point - the reason shared UI tests cost nothing.
 *
 * The same test code runs on desktop (headless, or a real window) and on an
 * Android device, so page objects, scenarios AND the tests themselves live in
 * commonTest, with no expect/actual on your side:
 *
 * ```kotlin
 * fun runTheaterTest(name: String, block: KabukiTestScope.(TheaterState) -> Unit) {
 *     runKabukiTest(name) {
 *         val app = TheaterState()
 *         setContent { TheaterApp(app) }
 *         block(app)
 *     }
 * }
 * ```
 *
 * For control over the desktop window use [runDesktopTest], available in JVM
 * test sources.
 */
public expect fun runKabukiTest(
    name: String = "Kabuki Test",
    profile: TestProfile = defaultTestProfile(),
    config: KabukiConfig.() -> Unit = {},
    block: KabukiTestScope.() -> Unit,
)
