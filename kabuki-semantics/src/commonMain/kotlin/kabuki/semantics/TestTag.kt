package kabuki.semantics

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

/**
 * Enum-to-tag conversion shared by production code ([testTag]) and test matchers
 * (`withTag(enum)`). Format: `"PlaybillTags.SCREEN"` - the simple class name,
 * because `KClass.qualifiedName` is unavailable on JS and wasm.
 */
public val Enum<*>.tagName: String
    get() {
        return "${this::class.simpleName}.$name"
    }

/**
 * Parameters attached to a test tag, kept separately from the tag itself.
 *
 * Stored as strings, so `1`, `1L` and `"1"` are the same parameter and the tree
 * dump stays readable. Use values with a stable toString.
 */
public val TestTagParamsKey: SemanticsPropertyKey<List<String>> =
    SemanticsPropertyKey("KabukiTagParams")

/**
 * Test tag from an enum entry, with optional parameters for repeated elements:
 *
 * ```kotlin
 * Modifier.testTag(PlaybillTags.SCREEN)
 * Modifier.testTag(SeatTags.SEAT, seat.row, seat.number)
 * ```
 *
 * The tag itself stays clean, so a test addresses either one element
 * (`node(SeatTags.SEAT, row, number)`) or the whole family (`nodeAll(SeatTags.SEAT)`).
 */
public fun Modifier.testTag(tag: Enum<*>, vararg params: Any): Modifier {
    val tagged = testTag(tag = tag.tagName)
    if (params.isEmpty()) {
        return tagged
    }
    val values = params.map { param -> param.toString() }
    return tagged.semantics { this[TestTagParamsKey] = values }
}
