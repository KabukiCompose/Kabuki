package kabuki

/**
 * OS fork: runs the branch matching the current [TestProfile.os].
 * A missing branch for the current OS is an explicit error - forks must be
 * exhaustive for every OS the test actually runs on.
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
 * Skips the test unless it runs on one of the [allowed] OSes
 * (the JUnit4/JUnit5 assumption mechanism when available on the classpath).
 */
public fun KabukiTestScope.assumeOs(vararg allowed: Os) {
    if (profile.os !in allowed) {
        skipTest("Test requires OS ${allowed.joinToString(" or ")}, current is ${profile.os}")
    }
}

/** Skips the test unless the window width size class matches [allowed]. */
public fun KabukiTestScope.assumeSizeClass(vararg allowed: SizeClass) {
    if (profile.sizeClass.width !in allowed) {
        skipTest(
            "Test requires width size class ${allowed.joinToString(" or ")}, " +
                "current is ${profile.sizeClass.width} (${profile.windowSize})",
        )
    }
}

/** Thrown by [skipTest] when no test-framework assumption exception is available. */
public class KabukiSkipException(message: String) : RuntimeException(message)

/**
 * Aborts the test as skipped: JUnit4 AssumptionViolatedException or JUnit5/opentest4j
 * TestAbortedException when present on the classpath, [KabukiSkipException] otherwise.
 */
public expect fun skipTest(reason: String): Nothing
