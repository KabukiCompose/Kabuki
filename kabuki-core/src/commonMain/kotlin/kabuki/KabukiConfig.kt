package kabuki

import kabuki.listener.ConsoleListener
import kabuki.listener.KabukiListener
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Per-test configuration: `runKabukiTest(config = { defaultTimeout = 10.seconds })`.
 *
 * An instance rather than a global object - every test gets its own copy, so
 * tests running in parallel within one JVM cannot affect each other.
 */
public class KabukiConfig {

    /** How long an operation keeps retrying. One node overrides it with [kabuki.page.UiNode.withTimeout]. */
    public var defaultTimeout: Duration = 5.seconds

    /** Extra pause between retry attempts. ZERO retries on every rendered frame. */
    public var pollingInterval: Duration = Duration.ZERO

    /**
     * Which semantics tree each kind of search looks at. Deliberately not a single
     * boolean: one switch answers two different questions and is wrong for half of
     * them - see [TreeStrategy].
     */
    public var treeStrategy: TreeStrategy = TreeStrategy.Smart

    /**
     * Interceptors replace HOW an operation is performed - which is what lets
     * [kabuki.page.UiNode.click] stay a single method with no strategy flags
     * ([ClickOnUiThread], [ClickViaSemanticsAction]).
     */
    public val interceptors: MutableList<KabukiInterceptor> = mutableListOf()

    /** Append a semantics tree dump to operation failure messages. */
    public var dumpSemanticsTreeOnFailure: Boolean = true

    /** Maximum number of tree dump lines attached to a failure message. */
    public var maxTreeDumpLines: Int = 50

    /** Lifecycle listeners; ConsoleListener is preinstalled. */
    public val listeners: MutableList<KabukiListener> = mutableListOf(ConsoleListener())

    /**
     * Let exceptions thrown by listeners fail the test. Off by default: a
     * listener observes the test, it should not decide its outcome - a reporter
     * that cannot write its file would otherwise turn a good test red. Turn it
     * on while developing a listener of your own.
     */
    public var strictListeners: Boolean = false

    /**
     * Reports a failure that is already on its way out. Never throws, even with
     * [strictListeners] on: a broken listener must not replace the real error.
     */
    internal fun notifyFailure(error: Throwable, block: KabukiListener.() -> Unit) {
        if (mutedDepth > 0) {
            return
        }
        for (listener in listeners) {
            try {
                listener.block()
            } catch (e: Throwable) {
                // Throwable, not Exception: an assert inside a listener throws an
                // AssertionError, and nothing a listener throws may outrank the
                // real failure. Suppressed does not always survive the trip out of
                // the test framework, hence the console too.
                error.addSuppressed(e)
                reportBrokenListener(listener, e, "while reporting a failure")
            }
        }
    }

    /** Depth of nested [muted] blocks - events are dropped while it is above zero. */
    private var mutedDepth: Int = 0

    /** Whether events are being dropped right now. */
    internal val isMuted: Boolean
        get() = mutedDepth > 0

    private var timeoutOverride: Duration? = null

    /** [defaultTimeout], unless something is overriding it - see [withDefaultTimeout]. */
    internal val currentDefaultTimeout: Duration
        get() = timeoutOverride ?: defaultTimeout

    /**
     * Runs [block] with a different default timeout - for a check that must answer
     * NOW, since waiting out its own timeout would eat the retry it should trigger.
     */
    internal fun <T> withDefaultTimeout(timeout: Duration, block: () -> T): T {
        val previous = timeoutOverride
        timeoutOverride = timeout
        return try {
            block()
        } finally {
            timeoutOverride = previous
        }
    }

    /** Runs [block] without telling the listeners - see [kabuki.page.UiNode.passed]. */
    internal fun <T> muted(block: () -> T): T {
        mutedDepth++
        return try {
            block()
        } finally {
            mutedDepth--
        }
    }

    /**
     * Notifies every listener, honouring [strictListeners]. Report through this
     * rather than iterating [listeners], which silently loses the isolation.
     */
    public fun notifyListeners(block: KabukiListener.() -> Unit) {
        if (mutedDepth > 0) {
            return
        }
        for (listener in listeners) {
            if (strictListeners) {
                listener.block()
                continue
            }
            try {
                listener.block()
            } catch (e: Throwable) {
                // Isolation that covers only Exception is not isolation.
                reportBrokenListener(listener, e, "")
            }
        }
    }

    private fun reportBrokenListener(listener: KabukiListener, error: Throwable, context: String) {
        val where = if (context.isEmpty()) "" else " $context"
        println(
            "[KABUKI] listener ${listener::class.simpleName} threw " +
                "${error::class.simpleName}$where: ${error.message}",
        )
    }
}

