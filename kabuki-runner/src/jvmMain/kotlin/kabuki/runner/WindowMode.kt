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

    /** CI (env CI / GITLAB_CI) -> Headless, locally -> Visible with defaults. */
    public data object Auto : WindowMode
}

internal fun WindowMode.resolve(): WindowMode {
    return when (this) {
        is WindowMode.Auto -> {
            val isCi = System.getenv("CI") != null || System.getenv("GITLAB_CI") != null
            if (isCi) WindowMode.Headless else WindowMode.Visible()
        }
        else -> this
    }
}
