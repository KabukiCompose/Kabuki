package kabuki

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Per-test configuration: `runKabukiTest(config = { defaultTimeout = 10.seconds })`.
 *
 * An instance rather than a global object - every test gets its own copy, so
 * tests running in parallel within one JVM cannot affect each other.
 */
public class KabukiConfig {

    /** How long an operation keeps retrying. One node overrides it with [UiNode.withTimeout]. */
    public var defaultTimeout: Duration = 5.seconds

    /** Extra pause between retry attempts. ZERO retries on every rendered frame. */
    public var pollingInterval: Duration = Duration.ZERO

    /**
     * Search the UNMERGED semantics tree: children keep their own tags instead of
     * being folded into a clickable parent, which is what makes `child(...)`
     * inside list items addressable at all.
     */
    public var useUnmergedTree: Boolean = true

    /**
     * Interceptors replace HOW an operation is performed - which is what lets
     * [UiNode.click] stay a single method with no strategy flags
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
     * Notifies every listener, honouring [strictListeners]. Report through this
     * rather than iterating [listeners], which silently loses the isolation.
     */
    public fun notifyListeners(block: KabukiListener.() -> Unit) {
        for (listener in listeners) {
            if (strictListeners) {
                listener.block()
                continue
            }
            try {
                listener.block()
            } catch (e: Exception) {
                // The listeners are what is broken here, so reporting through them
                // is not an option. Errors (OOM and friends) are left to propagate.
                println("[KABUKI] listener ${listener::class.simpleName} threw ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}

/**
 * Failure of a Kabuki operation: the node description, the last underlying
 * error and - unless disabled via [KabukiConfig.dumpSemanticsTreeOnFailure] -
 * a dump of the semantics tree as it was at the moment of failure.
 *
 * An AssertionError on purpose: test frameworks treat it as a failed assertion
 * rather than a crashed test.
 */
public class KabukiAssertionError(message: String, cause: Throwable? = null) : AssertionError(message, cause)
