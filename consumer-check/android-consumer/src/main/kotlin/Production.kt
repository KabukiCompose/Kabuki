package consumer.android

import androidx.compose.ui.Modifier
import kabuki.semantics.testListItem
import kabuki.semantics.testTag

/** Production code of an Android app: tags on a Modifier, nothing else. */
enum class AndroidTags { SCREEN, ROW }

@Suppress("unused")
fun taggedModifier(index: Int): Modifier {
    return Modifier
        .testTag(AndroidTags.SCREEN)
        .testTag(AndroidTags.ROW, "row", index)
        .testListItem(index)
}
