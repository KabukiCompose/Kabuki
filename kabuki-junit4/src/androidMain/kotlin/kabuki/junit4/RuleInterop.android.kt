package kabuki.junit4

import android.content.res.Resources
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kabuki.Os
import kabuki.Platform
import kabuki.TestProfile

/** Android: describes the real device the rule is running on - size and density. */
public actual fun defaultInteropProfile(): TestProfile {
    val configuration = Resources.getSystem().configuration
    val metrics = Resources.getSystem().displayMetrics
    return TestProfile(
        platform = Platform.Android,
        os = Os.Android,
        windowSize = DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp),
        density = metrics.density,
    )
}
