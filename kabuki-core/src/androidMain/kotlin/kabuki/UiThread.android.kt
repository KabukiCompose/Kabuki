package kabuki

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch

internal actual fun <T> runOnUiThread(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return block()
    }
    var result: T? = null
    var error: Throwable? = null
    val latch = CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        try {
            result = block()
        } catch (e: Throwable) {
            error = e
        } finally {
            latch.countDown()
        }
    }
    latch.await()
    error?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
