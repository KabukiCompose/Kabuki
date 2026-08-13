package kabuki.page

import androidx.compose.ui.test.SemanticsMatcher
import kabuki.KabukiTestScope
import kabuki.KabukiUsageError
import kabuki.internal.boundScope
import kabuki.semantics.tagName
import kotlin.time.Duration

/**
 * Base of the scoped DSL: an owner of [UiNode] declarations with a lazily bound
 * test scope. Nodes are declared as properties at construction time - before any
 * scope exists - and resolve the scope at operation time.
 *
 * No static state: the scope is injected per test via [bind] (done by `onScreen`),
 * so parallel tests within one JVM do not conflict.
 */
public abstract class NodeHost {

    private val boundScope = boundScope()
    private val children = mutableListOf<NodeHost>()

    /**
     * Page objects that declared this one via [component] and are entered right
     * now. A list rather than a single parent because a component may be an
     * `object`: one instance can be declared by several owners.
     */
    private val owners = mutableListOf<NodeHost>()

    internal fun addOwner(owner: NodeHost) {
        if (owners.none { existing -> existing === owner }) {
            owners += owner
        }
    }

    private fun removeOwner(owner: NodeHost) {
        owners.removeAll { existing -> existing === owner }
    }

    protected val scope: KabukiTestScope
        get() {
            // A page object that outlives its test (an `object`) is released when
            // the test finishes, so a stale binding shows up here as "not bound"
            // rather than as a hang on a scene that no longer exists. Entering a
            // finished test is refused earlier, in Screen.enter.
            return boundScope.get() ?: throw KabukiUsageError(
                "${this::class.simpleName} is not bound to a test scope. Enter its screen first: " +
                    "onScreen<Screen> { }, onScreen(ObjectScreen) { } or ObjectScreen { }.",
            )
        }

    internal fun bind(scope: KabukiTestScope) {
        boundScope.set(scope)
        // The scope releases every host it bound when the test ends: an `object`
        // page object would otherwise hold the finished test's scene alive until
        // the next test on this thread binds over it.
        scope.registerBoundHost(this)
        for (child in children) {
            // Re-established on every entry, because [unbind] drops it: a singleton
            // component must not accumulate one owner per onScreen call for the
            // whole run - it outlives every test that ever declared it.
            child.addOwner(this)
            child.bind(scope)
        }
    }

    /** Called by the scope when the test is over. Not recursive - children register themselves. */
    internal fun unbind() {
        boundScope.set(null)
        for (child in children) {
            child.removeOwner(this)
        }
    }

    /** The scope this host is bound to on this thread, or null. */
    internal fun bindingOrNull(): KabukiTestScope? {
        return boundScope.get()
    }

    /**
     * The node every declaration here is searched inside of, or null when this host
     * does not scope its nodes. A [Component] returns its root; a [Screen] does not
     * scope at all - dialogs and dropdowns are drawn OUTSIDE the screen's subtree,
     * so scoping a screen would hide half of what its page object describes.
     */
    internal open fun scopingRoot(): UiNode? {
        return null
    }

    /**
     * Container matcher for a node declared here, resolved through the chain of
     * enclosing hosts - a component nested in a component searches inside both.
     *
     * The identity check is what terminates the recursion: the root's own matcher
     * is built by asking this same method, so scoping the root by itself would
     * loop until the stack ends. The root is still scoped by the ENCLOSING host.
     * Pass null for declarations that can never be a root - collections and lists.
     */
    internal fun containerFor(node: UiNode?): SemanticsMatcher? {
        val root = scopingRoot()
        if (root != null && root !== node) {
            return root.effectiveMatcher()
        }
        // Own root aside, the container comes from whoever declared this host.
        // Several owners are fine as long as they all point at the SAME container:
        // entering one screen twice builds two screen instances, and both describe
        // the same place. Only genuinely different containers are ambiguous.
        val containers = owners
            .mapNotNull { owner -> owner.containerFor(node) }
            .distinctBy { container -> container.description }
        if (containers.size > 1) {
            throw KabukiUsageError(
                "${this::class.simpleName} is declared inside ${containers.size} page objects that scope " +
                    "their nodes differently, so there is no single container to search in: " +
                    containers.joinToString(", ") { container -> container.description } + ". " +
                    "One instance cannot be in two places at once - declare it as a class " +
                    "instead of an object.",
            )
        }
        return containers.firstOrNull()
    }

    /**
     * Declares a node: `node { withTag(Tags.TITLE) }`.
     * Conditions inside the builder are combined with AND.
     */
    protected fun node(timeout: Duration? = null, build: NodeMatcherBuilder.() -> Unit): UiNode {
        return uiNode(scopeProvider = { scope }, timeout = timeout, host = this, build = build)
    }

    /** Node by an enum tag - shorthand for `node { withTag(tag, params) }`. */
    protected fun node(tag: Enum<*>, vararg params: Any): UiNode {
        return node { withTag(tag, *params) }
    }

    /** All nodes matching the builder - count assertions and index access. */
    protected fun nodeAll(build: NodeMatcherBuilder.() -> Unit): UiNodeCollection {
        val builder = NodeMatcherBuilder().apply(build)
        return UiNodeCollection(
            scopeProvider = { scope },
            matcher = builder.buildMatcher(),
            description = "all ${builder.buildDescription()}",
            searchKind = builder.searchKind,
            host = this,
        )
    }

    /** All nodes with the enum tag. */
    protected fun nodeAll(tag: Enum<*>, vararg params: Any): UiNodeCollection {
        return nodeAll { withTag(tag, *params) }
    }

    /**
     * Declares a lazy list with typed items:
     *
     * ```kotlin
     * val reviews = lazyList(ReviewTags.LIST) { itemType(::ReviewItem) }
     * ```
     */
    protected fun lazyList(
        tag: Enum<*>,
        types: LazyListTypesBuilder.() -> Unit = {},
    ): LazyList {
        return lazyList(tag.tagName, types)
    }

    protected fun lazyList(
        tag: String,
        types: LazyListTypesBuilder.() -> Unit = {},
    ): LazyList {
        val builder = NodeMatcherBuilder().apply { withTag(tag) }
        return LazyList(
            scopeProvider = { scope },
            listMatcher = builder.buildMatcher(),
            description = "lazy list tag '$tag'",
            factories = LazyListTypesBuilder().apply(types).factories,
            host = this,
        )
    }

    /**
     * Declares a nested reusable component:
     * `val numpad = component(::NumpadComponent)`.
     * The component is bound to the same scope as its owner.
     */
    protected fun <C : Component<C>> component(factory: () -> C): C {
        val child = factory()
        children += child
        // The link the child searches through: its own root is looked for inside
        // this host's container, so nesting composes instead of resetting.
        child.addOwner(this)
        return child
    }
}
