package kabuki.internal

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import kabuki.KabukiAssertionError
import kabuki.KabukiTestScope
import kabuki.KabukiUsageError
import kabuki.Tree
import kabuki.listener.OperationInfo
import kabuki.listener.OperationResult
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * One node operation: report, retry until success or timeout, report the outcome
 * with attempts and duration. Every UI operation goes through here, so none can
 * be missing from the report.
 */
internal fun KabukiTestScope.runOperation(
    operation: String,
    nodeDescription: String,
    timeout: Duration,
    onTimeout: (cause: Throwable?, timeoutUsed: Duration) -> Throwable,
    block: () -> Unit,
) {
    // The last line of defence against the worst failure mode in this library: an
    // operation on a scene that no longer exists does not fail, it HANGS - the
    // virtual clock is stopped, so the retry loop waits for a frame that never comes.
    if (isFinished) {
        throw KabukiUsageError(
            "This test has already finished, so '$operation' on $nodeDescription cannot run. " +
                "A page object declared as an `object` outlives its test - enter it again " +
                "in the test that uses it.",
        )
    }

    val info = OperationInfo(operation = operation, node = nodeDescription)
    config.notifyListeners { onOperationStart(info) }

    val started = TimeSource.Monotonic.markNow()
    var attempts = 0
    val watchdog = startStallWatchdog(config, operation, nodeDescription, timeout)

    try {
        retryUntilSuccess(
            conditionDescription = "$operation on $nodeDescription",
            timeout = timeout,
            onTimeout = onTimeout,
        ) {
            attempts++
            block()
        }
    } catch (e: Throwable) {
        val failure = OperationResult.Failed(e, attempts, started.elapsedNow())
        config.notifyFailure(e) { onOperationFinish(info, failure) }
        throw e
    } finally {
        // cancel(null), not cancel(): the argument-less form goes through a
        // synthetic cancel$default, and coroutines 1.11 moved that one out of
        // Job$DefaultImpls into the interface. Built against one version, run
        // against the other - NoSuchMethodError.
        watchdog?.cancel(null)
    }

    val success = OperationResult.Succeeded(attempts, started.elapsedNow())
    config.notifyListeners { onOperationFinish(info, success) }
}

/**
 * The retry primitive: runs [block] until it stops throwing or [timeout] expires.
 * Built on ComposeUiTest.waitUntil (v2 API), which advances the virtual clock
 * between attempts. [kabuki.KabukiConfig.pollingInterval] adds a real-time pause; with
 * Duration.ZERO the loop retries on every rendered frame. On timeout a semantics
 * tree dump is appended (see [kabuki.KabukiConfig.dumpSemanticsTreeOnFailure]).
 *
 * Private: retrying without reporting should not be reachable - see [runOperation].
 */
@OptIn(ExperimentalTestApi::class)
private fun KabukiTestScope.retryUntilSuccess(
    conditionDescription: String,
    timeout: Duration,
    onTimeout: (cause: Throwable?, timeoutUsed: Duration) -> Throwable,
    block: () -> Unit,
) {
    var lastError: Throwable? = null
    val pollingMillis = config.pollingInterval.inWholeMilliseconds
    // setContent composes synchronously, so an empty scene after it is broken, not
    // slow. A scene Kabuki did not fill (interop over a foreign rule) still waits.
    val stopOnEmptyScene = contentInstalled

    fun retryAfter(error: Throwable): Boolean {
        if (stopOnEmptyScene) {
            emptySceneErrorOrNull(error)?.let { fatal -> throw fatal }
        }
        lastError = error
        pause(pollingMillis)
        return false
    }

    try {
        context.waitUntil(
            conditionDescription = conditionDescription,
            timeoutMillis = timeout.inWholeMilliseconds,
        ) {
            try {
                block()
                true
            } catch (e: AssertionError) {
                // No empty-scene check: Compose raises that as an ISE.
                lastError = e
                pause(pollingMillis)
                false
            } catch (e: KabukiUsageError) {
                // The page object is wrong, not the UI late. Waiting out the timeout
                // would hide the message that explains what to fix.
                throw e
            } catch (e: IllegalStateException) {
                retryAfter(e)
            } catch (e: IllegalArgumentException) {
                retryAfter(e)
            }
        }
    } catch (e: ComposeTimeoutException) {
        // Compose's timeout says nothing a reader needs - the real cause is in
        // lastError and goes into the message below.
        emptySceneErrorOrNull(lastError)?.let { fatal -> throw fatal }
        throw withTreeDump(onTimeout(lastError, timeout))
    }
}

private fun pause(millis: Long) {
    if (millis > 0) {
        sleepMillis(millis)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun KabukiTestScope.withTreeDump(error: Throwable): Throwable {
    // A probe swallows the message - no point printing the whole tree for nobody.
    if (config.isMuted || !config.dumpSemanticsTreeOnFailure || error !is KabukiAssertionError) {
        return error
    }
    // Dumped in the strategy's structural tree: under Smart that is the unmerged
    // one, which shows where a tag or a text physically sits - the question a
    // failing search actually raises.
    val tree = config.treeStrategy.structuralSearch
    val dump = runCatching {
        context.onRoot(useUnmergedTree = tree == Tree.Unmerged).printToString()
    }.getOrNull() ?: return error

    val lines = dump.lines()
    val shown = if (lines.size > config.maxTreeDumpLines) {
        // Both ends are kept on purpose. The head holds the screen structure -
        // roots, containers, the first items of a list; the tail holds whatever
        // was composed last. Dropping the head once hid exactly the nodes the
        // failing test was looking for.
        val headSize = config.maxTreeDumpLines / 2
        val tailSize = config.maxTreeDumpLines - headSize
        lines.take(headSize) +
            listOf("... (${lines.size - headSize - tailSize} lines omitted)") +
            lines.takeLast(tailSize)
    } else {
        lines
    }
    return KabukiAssertionError(
        message = buildString {
            appendLine(error.message)
            appendLine()
            appendLine("Semantics tree (${tree.name.lowercase()}) at the moment of failure:")
            shown.forEach { appendLine(it) }
        },
        cause = error.cause,
    )
}

/** Real-time sleep between retry attempts (does not touch the virtual clock). */
internal expect fun sleepMillis(millis: Long)
