package kabuki

import android.content.res.Resources
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Android: always [Os.Android] - nothing to detect. */
public actual fun detectOs(): Os {
    return Os.Android
}

/**
 * Profile built from the actual device: real screen size in dp and density -
 * size classes and forks behave exactly like on desktop.
 */
public actual fun defaultTestProfile(): TestProfile {
    val configuration = Resources.getSystem().configuration
    val metrics = Resources.getSystem().displayMetrics
    return TestProfile(
        platform = Platform.Android,
        os = Os.Android,
        windowSize = DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp),
        density = metrics.density,
    )
}
