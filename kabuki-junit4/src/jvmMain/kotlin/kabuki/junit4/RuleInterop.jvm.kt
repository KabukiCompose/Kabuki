package kabuki.junit4

import kabuki.Profiles
import kabuki.TestProfile

/**
 * Desktop: a nominal profile. The rule owns the real window, so this describes
 * the environment for assertions rather than resizing anything.
 */
public actual fun defaultInteropProfile(): TestProfile {
    return Profiles.Desktop.Default
}
