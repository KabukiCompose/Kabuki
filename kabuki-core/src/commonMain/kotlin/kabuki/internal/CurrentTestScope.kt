package kabuki.internal

import kabuki.KabukiTestScope

/**
 * The test running on THIS thread, so that a page object declared as an `object`
 * can be entered without naming the scope: `PlaybillScreen { }`.
 *
 * The one piece of ambient state in Kabuki, and deliberately per-thread: a global
 * one (which is what Ultron keeps) makes running tests in parallel inside one
 * process impossible. Set when a test scope is created, cleared when the runner
 * reports the test as finished.
 */
internal object CurrentTestScope {

    private val perThread = boundScope()

    fun get(): KabukiTestScope? {
        return perThread.get()
    }

    fun set(scope: KabukiTestScope?) {
        perThread.set(scope)
    }
}
