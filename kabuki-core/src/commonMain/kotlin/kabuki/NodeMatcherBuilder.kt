package kabuki

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import kabuki.semantics.TestTagParamsKey
import kabuki.semantics.tagName
import kotlin.time.Duration

/**
 * Matcher builder for `node { }` blocks. Multiple conditions are combined with AND:
 *
 * ```kotlin
 * node {
 *     withTag(Tags.SEAT_BUTTON)
 *     withAncestor { withTag(Tags.SEAT_LIST) }
 * }
 * ```
 */
@OptIn(ExperimentalTestApi::class)
public class NodeMatcherBuilder {
    private var combined: SemanticsMatcher? = null
    private val descriptions = mutableListOf<String>()

    /** Tag and params the node was addressed by - fuels failure diagnostics. */
    internal var diagnosticTag: String? = null
        private set
    internal var diagnosticParams: List<String> = emptyList()
        private set

    /** Raw string tag. Prefer the enum overload where the tag is yours to change. */
    public fun withTag(tag: String) {
        append(hasTestTag(tag), "tag '$tag'")
    }

    /**
     * Matches a test tag declared as an enum entry, using the shared
     * [tagName] convention from kabuki-semantics (the production side sets
     * the same tag via `Modifier.testTag(enum, ...)`).
     *
     * [params] target one element of a repeated family - pass the same values
     * production code passed to `Modifier.testTag`:
     * `withTag(SeatTags.SEAT, row, number)`. With no params the matcher
     * accepts every element carrying this tag.
     */
    public fun withTag(tag: Enum<*>, vararg params: Any) {
        val values = params.map { param -> param.toString() }
        diagnosticTag = tag.tagName
        diagnosticParams = values
        if (values.isEmpty()) {
            append(hasTestTag(tag.tagName), "tag '${tag.tagName}'")
            return
        }
        append(
            matcher = tagAndParamsMatcher(tag.tagName, values),
            description = "tag '${tag.tagName}' params $values",
        )
    }

    /** Matches by visible text. [substring] defaults to false here - an exact match. */
    public fun withText(text: String, substring: Boolean = false) {
        append(hasText(text, substring = substring), "text '$text'")
    }

    /** Matches by content description - the accessibility label, exact match. */
    public fun withContentDescription(description: String) {
        append(hasContentDescription(description), "contentDescription '$description'")
    }

    /** Requires any ancestor to match the nested builder. */
    public fun withAncestor(build: NodeMatcherBuilder.() -> Unit) {
        val ancestor = NodeMatcherBuilder().apply(build)
        append(hasAnyAncestor(ancestor.buildMatcher()), "ancestor(${ancestor.buildDescription()})")
    }

    /** Escape hatch: any raw SemanticsMatcher. */
    public fun matching(matcher: SemanticsMatcher) {
        append(matcher, matcher.description)
    }

    private fun append(matcher: SemanticsMatcher, description: String) {
        combined = combined?.and(matcher) ?: matcher
        descriptions += description
    }

    /** The combined matcher. Throws if no condition was added. */
    public fun buildMatcher(): SemanticsMatcher {
        return checkNotNull(combined) {
            "Node matcher is empty - add at least one condition (withTag, withText, ...)"
        }
    }

    /** Human-readable form of the conditions - what failure messages quote. */
    public fun buildDescription(): String {
        return descriptions.joinToString(" and ")
    }
}

/**
 * Factory for [UiNode] with a lazily resolved scope - the building block for
 * DSL layers (screens, components) and custom extensions.
 */
public fun uiNode(
    scopeProvider: () -> KabukiTestScope,
    timeout: Duration? = null,
    build: NodeMatcherBuilder.() -> Unit,
): UiNode {
    val builder = NodeMatcherBuilder().apply(build)
    return UiNode(
        scopeProvider = scopeProvider,
        matcher = builder.buildMatcher(),
        description = builder.buildDescription(),
        timeout = timeout,
        diagnosticTag = builder.diagnosticTag,
        diagnosticParams = builder.diagnosticParams,
    )
}

/** Matcher for a tag plus its parameters (see kabuki-semantics `Modifier.testTag`). */
@OptIn(ExperimentalTestApi::class)
internal fun tagAndParamsMatcher(tag: String, params: List<String>): SemanticsMatcher {
    val tagMatcher = hasTestTag(tag)
    if (params.isEmpty()) {
        return tagMatcher
    }
    return tagMatcher and SemanticsMatcher.expectValue(TestTagParamsKey, params)
}

