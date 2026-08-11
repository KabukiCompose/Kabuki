package kabuki

internal actual fun sleepMillis(millis: Long) {
    Thread.sleep(millis)
}
