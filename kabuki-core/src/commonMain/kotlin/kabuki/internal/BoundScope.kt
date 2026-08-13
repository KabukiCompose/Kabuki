package kabuki.internal

import kabuki.KabukiTestScope

/**
 * Holds the scope a page object is currently bound to, PER THREAD.
 *
 * Per thread rather than per instance because a page object may be declared as an
 * `object`: one instance shared by the whole JVM, while tests run in parallel by
 * threads. A plain field would hand one test the scope of another - the very
 * problem Kabuki avoids by keeping the context in the receiver.
 */
internal interface BoundScope {

    fun get(): KabukiTestScope?

    fun set(scope: KabukiTestScope?)
}

/**
 * Creates a per-thread holder. An expect FUNCTION rather than an expect class:
 * expect/actual classes are still in Beta, and the project builds with warnings
 * as errors.
 */
internal expect fun boundScope(): BoundScope
