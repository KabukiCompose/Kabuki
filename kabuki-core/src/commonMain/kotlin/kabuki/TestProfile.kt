package kabuki

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Where the test runs. Set by the runner, available as `profile.platform`. */
public enum class Platform {
    Desktop,
    Android,
    Web,
}

/**
 * Concrete operating system - finer than [Platform], because desktop behaviour
 * differs between systems. Used by `os()` branches and `assumeOs`.
 */
public enum class Os {
    Windows,
    Linux,
    MacOs,
    Android,
    Browser,
}

/** Screen orientation of the test scene. */
public enum class Orientation {
    Portrait,
    Landscape,
}

/** Material 3 window size class buckets (no dependency on material3). */
public enum class SizeClass {
    Compact,
    Medium,
    Expanded,
}

/** Size class along both axes: width (600/840 dp) and height (480/900 dp) thresholds. */
public data class WindowSizeClass(
    val width: SizeClass,
    val height: SizeClass,
)

/**
 * The test environment profile: platform, OS, window size and density.
 * Available in every test as `KabukiTestScope.profile`; drives the headless
 * scene size, the visible window size and the os()/layout() forks.
 */
public data class TestProfile(
    val platform: Platform,
    val os: Os,
    val windowSize: DpSize,
    val density: Float = 1f,
) {
    val orientation: Orientation
        get() {
            return if (windowSize.width >= windowSize.height) Orientation.Landscape else Orientation.Portrait
        }

    val sizeClass: WindowSizeClass
        get() {
            val width = when {
                windowSize.width < 600.dp -> SizeClass.Compact
                windowSize.width < 840.dp -> SizeClass.Medium
                else -> SizeClass.Expanded
            }
            val height = when {
                windowSize.height < 480.dp -> SizeClass.Compact
                windowSize.height < 900.dp -> SizeClass.Medium
                else -> SizeClass.Expanded
            }
            return WindowSizeClass(width, height)
        }
}

/** Detects the OS the test is currently running on. */
public expect fun detectOs(): Os

/**
 * Ready-made environment profiles, so a test states its conditions instead of
 * inheriting whatever the machine happens to have:
 *
 * ```kotlin
 * runDesktopTest(profile = Profiles.Desktop.FullHd) { ... }
 * ```
 *
 * A profile fixes the scene size, density and therefore the window size class -
 * the same test then behaves identically on any machine and on CI.
 */
public object Profiles {

    /** Desktop presets. The OS is detected at runtime, only the size is fixed. */
    public object Desktop {
        /** Laptop-sized window - the default for desktop runs. */
        public val Default: TestProfile
            get() {
                return profile(width = 1280, height = 800)
            }

        /** Full HD - an expanded window where everything fits without scrolling. */
        public val FullHd: TestProfile
            get() {
                return profile(width = 1920, height = 1080)
            }

        /** A typical point-of-sale / small laptop screen. */
        public val SmallHd: TestProfile
            get() {
                return profile(width = 1366, height = 768)
            }

        /** A deliberately cramped window - catches layouts that assume space. */
        public val CompactWindow: TestProfile
            get() {
                return profile(width = 800, height = 600)
            }

        /** A custom desktop profile: size in dp, plus an optional density. */
        public fun profile(width: Int, height: Int, density: Float = 1f): TestProfile {
            return TestProfile(
                platform = Platform.Desktop,
                os = detectOs(),
                windowSize = DpSize(width.dp, height.dp),
                density = density,
            )
        }
    }

    /**
     * Android presets. On a real device the window size is dictated by the
     * device itself - these describe the environment for assertions and
     * `assumeSizeClass`, they do not resize anything.
     */
    public object Android {
        /** 10" tablet in landscape - the expanded size class. */
        public val Tablet10Landscape: TestProfile
            get() {
                return profile(width = 1280, height = 800)
            }

        /** A typical phone in portrait - the compact size class. */
        public val PhonePortrait: TestProfile
            get() {
                return profile(width = 411, height = 891)
            }

        /** A custom Android profile: size in dp, plus an optional density. */
        public fun profile(width: Int, height: Int, density: Float = 1f): TestProfile {
            return TestProfile(
                platform = Platform.Android,
                os = Os.Android,
                windowSize = DpSize(width.dp, height.dp),
                density = density,
            )
        }
    }
}

/**
 * Platform default profile: desktop window presets on JVM, the real device
 * configuration on Android. Used by runners and by kabuki-junit4 when no
 * profile is passed explicitly.
 */
public expect fun defaultTestProfile(): TestProfile
