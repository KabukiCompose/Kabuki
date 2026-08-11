package kabuki.runner

import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.TestProfile
import kabuki.defaultTestProfile

/**
 * Desktop: delegates to [runDesktopTest] with [WindowMode.Auto] - headless by
 * default, with a visible window when the run asks for one. Use `runDesktopTest`
 * directly to pin the window mode.
 */
public actual fun runKabukiTest(
    name: String,
    profile: TestProfile,
    config: KabukiConfig.() -> Unit,
    block: KabukiTestScope.() -> Unit,
) {
    runDesktopTest(
        name = name,
        profile = profile,
        window = WindowMode.Auto,
        config = config,
        block = block,
    )
}
