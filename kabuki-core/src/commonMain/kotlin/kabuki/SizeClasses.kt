package kabuki

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width bucket of the test window.
 *
 * A type of its own rather than one enum shared with [HeightClass]: the
 * thresholds differ per axis, so a shared value would not remember where it came
 * from, and comparing a width to a height would compile while meaning nothing.
 *
 * [Large] and [ExtraLarge] are what make desktop testable - without them every
 * window from 840dp up lands in one bucket and the desktop presets stop being
 * distinguishable.
 */
public enum class WidthClass {
    Compact,
    Medium,
    Expanded,
    Large,
    ExtraLarge,
}

/** Height bucket of the test window. A separate type from [WidthClass], see there. */
public enum class HeightClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Lower bounds of the width buckets; defaults are the Material ones.
 *
 * Override them when the app has breakpoints of its own - a size class is the
 * APP'S vocabulary, not a universal truth. A sidebar that appears at 700dp is
 * still `Medium` by the Material table, so on an 800dp window a test asking for
 * [WidthClass.Expanded] would quietly run no checks at all:
 *
 * ```kotlin
 * runKabukiTest(config = { widthBreakpoints = WidthBreakpoints(medium = 700.dp) })
 * ```
 *
 * Parameter names are the class names, values are the lower bound of each class;
 * [WidthClass.Compact] is everything below [medium] and needs no bound.
 */
public data class WidthBreakpoints(
    val medium: Dp = 600.dp,
    val expanded: Dp = 840.dp,
    val large: Dp = 1200.dp,
    val extraLarge: Dp = 1600.dp,
) {
    init {
        require(medium < expanded && expanded < large && large < extraLarge) {
            "Width breakpoints must grow, got medium=$medium expanded=$expanded " +
                "large=$large extraLarge=$extraLarge"
        }
    }

    /** The bucket [width] falls into. */
    public fun classOf(width: Dp): WidthClass {
        return when {
            width < medium -> WidthClass.Compact
            width < expanded -> WidthClass.Medium
            width < large -> WidthClass.Expanded
            width < extraLarge -> WidthClass.Large
            else -> WidthClass.ExtraLarge
        }
    }
}

/** Lower bounds of the height buckets; defaults are the Material ones. See [WidthBreakpoints]. */
public data class HeightBreakpoints(
    val medium: Dp = 480.dp,
    val expanded: Dp = 900.dp,
) {
    init {
        require(medium < expanded) {
            "Height breakpoints must grow, got medium=$medium expanded=$expanded"
        }
    }

    /** The bucket [height] falls into. */
    public fun classOf(height: Dp): HeightClass {
        return when {
            height < medium -> HeightClass.Compact
            height < expanded -> HeightClass.Medium
            else -> HeightClass.Expanded
        }
    }
}

/**
 * Width bucket of this test's window.
 *
 * On the scope rather than on [TestProfile], because the thresholds live in
 * [KabukiConfig]: they describe the application, and one project has many
 * profiles but a single set of breakpoints.
 */
public val KabukiTestScope.widthClass: WidthClass
    get() {
        return config.widthBreakpoints.classOf(profile.windowSize.width)
    }

/** Height bucket of this test's window. See [widthClass]. */
public val KabukiTestScope.heightClass: HeightClass
    get() {
        return config.heightBreakpoints.classOf(profile.windowSize.height)
    }
