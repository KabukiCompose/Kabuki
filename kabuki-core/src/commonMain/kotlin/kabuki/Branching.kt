package kabuki

import androidx.compose.ui.unit.Dp

/**
 * OS fork: runs the branch matching the current [TestProfile.os].
 * A missing branch for the current OS is an explicit error - forks must be
 * exhaustive for every OS the test actually runs on.
 *
 * Use this when one step is done DIFFERENTLY everywhere. When a step applies
 * only somewhere and is meant to be absent elsewhere, that is [onlyOnOs] - it
 * cannot catch a forgotten branch, and is not supposed to.
 */
public fun KabukiTestScope.os(
    windows: (() -> Unit)? = null,
    linux: (() -> Unit)? = null,
    macos: (() -> Unit)? = null,
    android: (() -> Unit)? = null,
    web: (() -> Unit)? = null,
) {
    val branch = when (profile.os) {
        Os.Windows -> windows
        Os.Linux -> linux
        Os.MacOs -> macos
        Os.Android -> android
        Os.Browser -> web
    } ?: error(
        "os() fork has no branch for the current OS ${profile.os}. " +
            "Declared branches: " + listOfNotNull(
            windows?.let { "windows" },
            linux?.let { "linux" },
            macos?.let { "macos" },
            android?.let { "android" },
            web?.let { "web" },
        ).joinToString(", "),
    )
    branch()
}

/**
 * Runs [block] only on the listed OSes, and moves on everywhere else.
 *
 * For a platform-specific INSERT inside a shared test. A whole test wrapped in
 * one of these passes everywhere else having checked nothing - a test that
 * belongs to one platform belongs in that platform's source set instead.
 */
public fun KabukiTestScope.onlyOnOs(os: Os, vararg more: Os, block: () -> Unit) {
    val allowed = setOf(os, *more)
    branch("onlyOnOs", allowed, profile.os, block)
}

/**
 * Runs [block] only on the listed platforms. [Platform] groups OSes, so
 * `onlyOnPlatform(Platform.Desktop)` says what listing three desktop OSes would.
 */
public fun KabukiTestScope.onlyOnPlatform(platform: Platform, vararg more: Platform, block: () -> Unit) {
    val allowed = setOf(platform, *more)
    branch("onlyOnPlatform", allowed, profile.platform, block)
}

/** Runs [block] only in the given orientation. */
public fun KabukiTestScope.onlyOnOrientation(orientation: Orientation, block: () -> Unit) {
    branch("onlyOnOrientation", setOf(orientation), profile.orientation, block)
}

/**
 * Runs [block] only in the listed width buckets. The bounds come from
 * [KabukiConfig.widthBreakpoints] - set them if the app uses its own.
 */
public fun KabukiTestScope.onlyOnWidthClass(widthClass: WidthClass, vararg more: WidthClass, block: () -> Unit) {
    val allowed = setOf(widthClass, *more)
    branch("onlyOnWidthClass", allowed, this.widthClass, block)
}

/** Runs [block] only in the listed height buckets. See [onlyOnWidthClass]. */
public fun KabukiTestScope.onlyOnHeightClass(heightClass: HeightClass, vararg more: HeightClass, block: () -> Unit) {
    val allowed = setOf(heightClass, *more)
    branch("onlyOnHeightClass", allowed, this.heightClass, block)
}

/**
 * Runs [block] only when the window is at least [width] wide.
 *
 * The escape hatch from buckets: a project with a vocabulary of its own keeps
 * the numbers on its side and needs nothing from Kabuki to do it.
 *
 * ```kotlin
 * object Breaks { val tablet = 700.dp }
 * onlyOnWidthAtLeast(Breaks.tablet) { sidebar.assertIsDisplayed() }
 * ```
 */
public fun KabukiTestScope.onlyOnWidthAtLeast(width: Dp, block: () -> Unit) {
    measured("onlyOnWidthAtLeast($width)", profile.windowSize.width >= width, "width", profile.windowSize.width, block)
}

/** Runs [block] only when the window is narrower than [width]. See [onlyOnWidthAtLeast]. */
public fun KabukiTestScope.onlyOnWidthBelow(width: Dp, block: () -> Unit) {
    measured("onlyOnWidthBelow($width)", profile.windowSize.width < width, "width", profile.windowSize.width, block)
}

/** Runs [block] only when the window is at least [height] tall. See [onlyOnWidthAtLeast]. */
public fun KabukiTestScope.onlyOnHeightAtLeast(height: Dp, block: () -> Unit) {
    val current = profile.windowSize.height
    measured("onlyOnHeightAtLeast($height)", current >= height, "height", current, block)
}

/** Runs [block] only when the window is shorter than [height]. See [onlyOnWidthAtLeast]. */
public fun KabukiTestScope.onlyOnHeightBelow(height: Dp, block: () -> Unit) {
    val current = profile.windowSize.height
    measured("onlyOnHeightBelow($height)", current < height, "height", current, block)
}

/**
 * Shared body of the `onlyOn*` family.
 *
 * Both outcomes reach the listeners. A block that did not run leaves no trace in
 * the test itself, and silence is exactly how a test ends up green having checked
 * nothing - so the report says which blocks were skipped and why.
 */
private fun <T> KabukiTestScope.branch(name: String, allowed: Set<T>, current: T, block: () -> Unit) {
    report(
        description = "$name(${allowed.joinToString(" or ")})",
        matches = current in allowed,
        actual = "current is $current",
        block = block,
    )
}

/** [branch] for the raw-dp forms, where the condition is a comparison rather than a set. */
private fun KabukiTestScope.measured(
    description: String,
    matches: Boolean,
    axis: String,
    current: Dp,
    block: () -> Unit,
) {
    report(description = description, matches = matches, actual = "$axis is $current", block = block)
}

private fun KabukiTestScope.report(
    description: String,
    matches: Boolean,
    actual: String,
    block: () -> Unit,
) {
    if (matches) {
        log("$description: running")
        block()
    } else {
        log("$description: skipped, $actual")
    }
}
