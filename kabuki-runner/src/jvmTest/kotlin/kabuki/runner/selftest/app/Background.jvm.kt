package kabuki.runner.selftest.app

import kotlin.concurrent.thread

internal actual fun runInBackground(delayMillis: Long, block: () -> Unit) {
    thread(isDaemon = true) {
        Thread.sleep(delayMillis)
        block()
    }
}
