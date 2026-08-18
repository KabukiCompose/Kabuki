package kabuki

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import kabuki.listener.KabukiListener
import kabuki.listener.StepInfo
import kabuki.listener.StepResult
import kabuki.listener.TestInfo
import kabuki.listener.TestResult
import kabuki.internal.CurrentTestScope
import kabuki.page.NodeHost
import kabuki.page.NodeMatcherBuilder
import kabuki.page.UiNode
import kabuki.page.UiNodeCollection
import kabuki.page.tagAndParamsMatcher
import kabuki.page.uiNode
import kabuki.semantics.tagName

/**
 * Test scope: the context lives in the block receiver - no static state, parallel
 * tests within one JVM do not conflict.
 *
 * Created by a runner (kabuki-runner), not by tests directly.
 * [onSetContent] is a runner hook invoked before the content is installed into
 * the scene (e.g. to mirror the same content into a visible window).
 */
@OptIn(ExperimentalTestApi::class)
public class KabukiTestScope(
    /** The Compose test API behind the DSL - the escape hatch for anything Kabuki does not wrap. */
    public val context: KabukiComposeContext,
    /** Timeouts, interceptors and listeners for THIS test - one instance per test. */
    public val config: KabukiConfig,
    /** The environment this test runs in: platform, OS, size. Drives `os()` and `assumeSizeClass`. */
    public val profile: TestProfile,
    private val onSetContent: ((content: @Composable () -> Unit) -> Unit)? = null,
) {
    private val stepCounters = mutableListOf(0)

    /**
     * Set once the runner reports the test as over. Read by page objects that
     * outlive a single test (an `object` screen), so that a stale binding fails
     * with an explanation instead of touching a scene that no longer exists.
     */
    internal var isFinished: Boolean = false
        private set

    private val boundHosts = mutableListOf<NodeHost>()

    /** What was running on this thread before, so that nesting restores it. */
    private val previousScope = CurrentTestScope.get()

    init {
        // Makes `PlaybillScreen { }` possible: an object screen has no way to be
        // handed the scope, so it asks the thread which test is running.
        // Declared AFTER the fields above - an init block runs in declaration
        // order, and this one publishes `this` while the object is being built.
        CurrentTestScope.set(this)
    }

    /** Page objects bound to this test, so that the binding can be dropped when it ends. */
    internal fun registerBoundHost(host: NodeHost) {
        boundHosts += host
    }

    /**
     * Whether this test filled the scene itself. An empty scene after [setContent]
     * is a broken setup; before it, the content may still be on its way.
     */
    internal var contentInstalled: Boolean = false
        private set

    /** Installs the content into the headless scene (and, via the runner hook, into a visible window). */
    public fun setContent(content: @Composable () -> Unit) {
        onSetContent?.invoke(content)
        context.setContent(content)
        context.waitForIdle()
        contentInstalled = true
    }

    /**
     * Scenario step: numbered 1, 1.1, 1.2... via listeners, unlimited nesting.
     *
     * Returns nothing on purpose. A step is a stage of a scenario - something
     * that HAPPENS - not an expression that produces a value. Making it generic
     * invites using steps as a general-purpose grouping wrapper anywhere in the
     * code, which turns the test specification into log decoration.
     */
    public fun step(description: String, block: () -> Unit) {
        // Inside a probe there is nobody to tell, and counting anyway would leave a
        // hole among the steps that ARE reported: "1, 3" reads as a lost step.
        if (config.isMuted) {
            block()
            return
        }
        stepCounters[stepCounters.lastIndex]++
        val info = StepInfo(label = stepCounters.joinToString("."), description = description)
        notifyListeners { onStepStart(info) }
        stepCounters.add(0)
        try {
            block()
            notifyOutcome(error = null) { onStepFinish(info, StepResult.Passed) }
        } catch (e: Throwable) {
            notifyOutcome(e) { onStepFinish(info, StepResult.Failed(e)) }
            throw e
        } finally {
            stepCounters.removeAt(stepCounters.lastIndex)
        }
    }

    /**
     * Node by a raw string tag. Prefer the enum overload: it survives renames and
     * gives the failure message a tag name to work with. This one is for tags
     * that are not yours to change (a third-party screen, a generated tag).
     */
    public fun node(tag: String): UiNode {
        return UiNode(scopeProvider = { this }, matcher = hasTestTag(tag), description = "tag '$tag'")
    }

    /**
     * Node by an enum tag (the [tagName] convention shared with production code),
     * optionally narrowed to one element of a repeated family:
     *
     * ```kotlin
     * node(PlaybillTags.SCREEN)              // plain tag
     * node(SeatTags.SEAT, row, number)       // one seat
     * ```
     */
    public fun node(tag: Enum<*>, vararg params: Any): UiNode {
        val values = params.map { param -> param.toString() }
        return UiNode(
            scopeProvider = { this },
            matcher = tagAndParamsMatcher(tag.tagName, values),
            description = if (values.isEmpty()) "tag '${tag.tagName}'" else "tag '${tag.tagName}' params $values",
            diagnosticTag = tag.tagName,
            diagnosticParams = values,
        )
    }

    /** Node with a matcher builder: `node { withTag(...); withAncestor { ... } }`. */
    public fun node(build: NodeMatcherBuilder.() -> Unit): UiNode {
        return uiNode(scopeProvider = { this }, build = build)
    }

    /**
     * Node by its visible text - for elements nobody tagged, such as a snackbar
     * or a system-provided label. Tags are the sturdier choice where you own the code.
     */
    public fun nodeWithText(text: String, substring: Boolean = true): UiNode {
        return UiNode(
            scopeProvider = { this },
            matcher = hasText(text, substring = substring),
            description = "text '$text'",
        )
    }

    /** All nodes with the tag - count assertions and index access. */
    public fun nodeAll(tag: String): UiNodeCollection {
        return UiNodeCollection(scopeProvider = { this }, matcher = hasTestTag(tag), description = "all tag '$tag'")
    }

    /** All nodes carrying the tag - with no params that is the whole repeated family. */
    public fun nodeAll(tag: Enum<*>, vararg params: Any): UiNodeCollection {
        val values = params.map { param -> param.toString() }
        return UiNodeCollection(
            scopeProvider = { this },
            matcher = tagAndParamsMatcher(tag.tagName, values),
            description = if (values.isEmpty()) "all tag '${tag.tagName}'" else "all tag '${tag.tagName}' params $values",
        )
    }

    /** All nodes matching a builder - the collection form of `node { }`. */
    public fun nodeAll(build: NodeMatcherBuilder.() -> Unit): UiNodeCollection {
        val builder = NodeMatcherBuilder().apply(build)
        return UiNodeCollection(
            scopeProvider = { this },
            matcher = builder.buildMatcher(),
            description = "all ${builder.buildDescription()}",
        )
    }

    /** Free-form message to all listeners (ConsoleListener prints it). */
    public fun log(message: String) {
        notifyListeners { onLog(message) }
    }

    /** For runner implementations: fires onTestStart on all listeners. */
    public fun notifyTestStart(test: TestInfo) {
        notifyListeners { onTestStart(test) }
    }

    /**
     * For runner implementations: fires onTestFinish on all listeners, once.
     *
     * A second call is ignored - a reporter must see one finished test, not one per
     * call. Runners shipped with Kabuki report the outcome from a single place; the
     * guard is here for anyone else's.
     */
    public fun notifyTestFinish(test: TestInfo, result: TestResult) {
        if (isFinished) {
            return
        }
        // finally, because with strictListeners a broken listener throws right here
        // - and skipping the cleanup would leave an `object` page object bound to a
        // scene that is already gone, where the next operation HANGS rather than fails.
        try {
            notifyOutcome((result as? TestResult.Failed)?.error) { onTestFinish(test, result) }
        } finally {
            isFinished = true
            // Let go of the page objects. A screen declared as `object` outlives the
            // test, and holding this scope from it would keep the finished test's
            // scene in memory until something binds over it.
            boundHosts.forEach { host -> host.unbind() }
            boundHosts.clear()
            // Restore rather than clear: an inner scope (interop inside a Kabuki test,
            // for instance) must not leave the outer test without a current scope.
            CurrentTestScope.set(previousScope)
        }
    }

    /**
     * Reports an outcome, picking the channel by whether it carries a failure.
     * Routing everything through here means the rule "a broken listener never
     * replaces the real error" cannot be forgotten at a new call site.
     */
    private fun notifyOutcome(error: Throwable?, block: KabukiListener.() -> Unit) {
        if (error == null) {
            notifyListeners(block)
        } else {
            config.notifyFailure(error, block)
        }
    }

    private fun notifyListeners(block: KabukiListener.() -> Unit) {
        config.notifyListeners(block)
    }
}
