package kabuki

/**
 * Executes the block on the platform UI thread (Swing EDT on desktop) and returns the result.
 * Exceptions thrown by the block are rethrown to the caller as is.
 */
internal expect fun <T> runOnUiThread(block: () -> T): T
