package kabuki.internal

import kabuki.KabukiTestScope

internal actual fun boundScope(): BoundScope {
    return ThreadLocalBoundScope()
}

private class ThreadLocalBoundScope : BoundScope {

    private val perThread = ThreadLocal<KabukiTestScope?>()

    override fun get(): KabukiTestScope? {
        return perThread.get()
    }

    override fun set(scope: KabukiTestScope?) {
        perThread.set(scope)
    }
}
