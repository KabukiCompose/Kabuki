package kabuki.runner.selftest.app

/**
 * Runs [block] on a REAL background thread after [delayMillis] of real time.
 *
 * Deliberately not a coroutine: the test clock is virtual, and a coroutine delay
 * would be fast-forwarded by the very retry loop under test. This is the one thing
 * in the self-test app that has no common form.
 */
internal expect fun runInBackground(delayMillis: Long, block: () -> Unit)
