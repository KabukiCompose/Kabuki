package kabuki.runner

import kabuki.KabukiConfig
import kabuki.KabukiTestScope
import kabuki.TestProfile

/**
 * Android: delegates to [runAndroidTest], which runs on the device or emulator.
 * There is no window mode here - the device screen is the window.
 */
public actual fun runKabukiTest(
    name: String,
    profile: TestProfile,
    config: KabukiConfig.() -> Unit,
    block: KabukiTestScope.() -> Unit,
) {
    runAndroidTest(
        name = name,
        profile = profile,
        config = config,
        block = block,
    )
}
