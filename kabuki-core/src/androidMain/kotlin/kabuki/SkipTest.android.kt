package kabuki

/**
 * Android: instrumented tests run on JUnit 4, so skipping surfaces as an
 * AssumptionViolatedException and the test is reported as skipped, not failed.
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
