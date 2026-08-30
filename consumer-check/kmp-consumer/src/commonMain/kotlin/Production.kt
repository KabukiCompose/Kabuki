package consumer.kmp

import androidx.compose.ui.Modifier
import kabuki.semantics.testListLength
import kabuki.semantics.testTag

/** Production code of a multiplatform app: tags, and nothing else from Kabuki. */
enum class KmpTags { SCREEN, LIST }

@Suppress("unused")
fun taggedModifier(itemCount: Int): Modifier {
    return Modifier
        .testTag(KmpTags.SCREEN)
        .testListLength(itemCount)
}
