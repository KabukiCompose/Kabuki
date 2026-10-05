package kabuki.semantics

import kotlin.test.Test
import kotlin.test.assertEquals

/** A plain tag enum - the shape every example uses. */
private enum class PlainTags { SCREEN }

/**
 * A tag enum whose constants carry bodies. Kotlin compiles each into an
 * anonymous subclass, which is where a name built from `simpleName` can go wrong.
 */
private enum class BodiedTags {
    SCREEN { override fun describe(): String = "screen" },
    LIST { override fun describe(): String = "list" },
    ;

    abstract fun describe(): String
}

/** An enum named like one of its own entries - the case the two platforms resolve differently. */
private enum class Playbill { Playbill, CARD }

/**
 * Addressing rests on production code and tests deriving the SAME string, so the
 * shape of that string is pinned here - including for enums the examples never show.
 *
 * It lives in this module rather than next to the runners because the name is built
 * per platform: JVM and Android read it off `javaClass`, iOS off `qualifiedName`.
 * Here every target compiles the test, so a platform that drifts says so.
 */
class TagNameSelfTest {

    @Test
    fun aPlainEnumNamesItsClass() {
        assertEquals("PlainTags.SCREEN", PlainTags.SCREEN.tagName)
    }

    @Test
    fun anEnumWithBodiedConstantsNamesItsClassToo() {
        // Anonymous subclasses must not leak into the tag - two different enums
        // would collide on the same prefix.
        assertEquals("BodiedTags.SCREEN", BodiedTags.SCREEN.tagName)
        assertEquals("BodiedTags.LIST", BodiedTags.LIST.tagName)
    }

    @Test
    fun anEnumNamedAfterItsOwnEntryStillNamesTheClass() {
        // The entry name matching the class name is what makes the iOS path
        // ambiguous, so both readings are pinned: the enum, never the package.
        assertEquals("Playbill.Playbill", Playbill.Playbill.tagName)
        assertEquals("Playbill.CARD", Playbill.CARD.tagName)
    }
}
