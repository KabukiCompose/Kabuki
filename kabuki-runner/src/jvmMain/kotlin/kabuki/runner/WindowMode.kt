package kabuki.runner

/**
 * Whether a desktop test also opens a real window next to the headless scene.
 *
 * Assertions always run against the headless scene; the window is a mirror for
 * human eyes - handy while writing a test, useless on CI. Hence [Auto] as the
 * default: visible locally, headless on CI.
 *
 * The window is never driven by java.awt.Robot, so it does not grab the system
 * cursor and several tests can run at once.
 */
public sealed interface WindowMode {
    /** Off-screen runComposeUiTest scene only. */
    public data object Headless : WindowMode

    /**
     * A real window with the same content is opened alongside the headless scene.
     * The window size and density come from the test profile - the window always
     * mirrors the headless scene 1:1.
     */
    public data class Visible(
        val title: String = "Kabuki UI Test",
        val alwaysOnTop: Boolean = true,
    ) : WindowMode

    /**
     * Decided at run time: `-Dkabuki.window=true|false` if set, otherwise headless
     * on CI and visible locally. A window is welcome while writing one test and
     * unbearable across a suite - hence the switch.
     */
    public data object Auto : WindowMode
}

/** System property that decides [WindowMode.Auto]: `-Dkabuki.window=false` keeps windows off. */
internal const val WINDOW_PROPERTY: String = "kabuki.window"

internal fun WindowMode.resolve(): WindowMode {
    return when (this) {
        is WindowMode.Auto -> if (windowsWanted()) WindowMode.Visible() else WindowMode.Headless
        else -> this
    }
}

/** An explicit answer beats a guess; without one, CI means headless. */
private fun windowsWanted(): Boolean {
    System.getProperty(WINDOW_PROPERTY)?.let { asked ->
        return asked.toBooleanStrictOrNull() ?: true
    }
    return System.getenv("CI") == null && System.getenv("GITLAB_CI") == null
}
