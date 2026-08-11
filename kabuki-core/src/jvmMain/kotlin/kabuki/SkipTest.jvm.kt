package kabuki

/**
 * Desktop: skipping is reported through whichever test framework is on the
 * classpath - JUnit 4's AssumptionViolatedException or JUnit 5's
 * TestAbortedException. With neither, the test fails with [KabukiSkipException]
 * instead of being silently ignored.
 */
public actual fun skipTest(reason: String): Nothing {
    throw createAssumptionException(reason)
}

/**
 * Builds a framework-specific "assumption failed" exception via reflection -
 * kabuki-core carries no compile dependency on any test framework.
 */
internal fun createAssumptionException(reason: String): Throwable {
    val candidates = listOf(
        "org.junit.AssumptionViolatedException",
        "org.opentest4j.TestAbortedException",
    )
    for (className in candidates) {
        val exception = runCatching {
            Class.forName(className)
                .getConstructor(String::class.java)
                .newInstance(reason) as Throwable
        }.getOrNull()
        if (exception != null) {
            return exception
        }
    }
    return KabukiSkipException(reason)
}
