package kabuki.internal

internal actual fun sleepMillis(millis: Long) {
    Thread.sleep(millis)
}
