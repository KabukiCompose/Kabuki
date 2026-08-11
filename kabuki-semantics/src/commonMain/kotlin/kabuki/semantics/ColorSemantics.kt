package kabuki.semantics

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

/**
 * Colors are not part of the semantics tree, so backgrounds and icon tints are
 * not testable out of the box (text color IS testable via the text layout -
 * see `UiNode.assertTextColor`). For everything else production code publishes
 * the color explicitly:
 *
 * ```kotlin
 * Box(Modifier.background(PosterColor).testBackgroundColor(PosterColor))
 * Icon(..., tint = AccentColor, modifier = Modifier.testTintColor(AccentColor))
 * ```
 */
public val BackgroundColorKey: SemanticsPropertyKey<Color> = SemanticsPropertyKey("KabukiBackgroundColor")

/** Tint of icons and decorations, published via [Modifier.testTintColor]. */
public val TintColorKey: SemanticsPropertyKey<Color> = SemanticsPropertyKey("KabukiTintColor")

/** Publishes the node's background color for tests. See [BackgroundColorKey]. */
public fun Modifier.testBackgroundColor(color: Color): Modifier {
    return semantics { this[BackgroundColorKey] = color }
}

/** Publishes the node's tint color (icons, decorations) for tests. See [TintColorKey]. */
public fun Modifier.testTintColor(color: Color): Modifier {
    return semantics { this[TintColorKey] = color }
}
