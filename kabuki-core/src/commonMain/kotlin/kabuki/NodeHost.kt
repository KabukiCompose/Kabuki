package kabuki

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

    private var boundScope: KabukiTestScope? = null
    private val children = mutableListOf<NodeHost>()

    protected val scope: KabukiTestScope
        get() {
            return checkNotNull(boundScope) {
                "${this::class.simpleName} is not bound to a test scope. " +
                    "Use it inside onScreen { } or bind it explicitly."
            }
        }

    internal fun bind(scope: KabukiTestScope) {
        boundScope = scope
        for (child in children) {
            child.bind(scope)
        }
    }

    /**
     * Declares a node: `node { withTag(Tags.TITLE) }`.
     * Conditions inside the builder are combined with AND.
     */
    protected fun node(timeout: Duration? = null, build: NodeMatcherBuilder.() -> Unit): UiNode {
        return uiNode(scopeProvider = { scope }, timeout = timeout, build = build)
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
        return child
    }
}
