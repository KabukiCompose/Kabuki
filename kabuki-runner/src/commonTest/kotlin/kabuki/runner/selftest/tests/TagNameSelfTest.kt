package kabuki.runner.selftest.tests

import kabuki.semantics.tagName
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

/**
 * Addressing rests on production code and tests deriving the SAME string, so the
 * shape of that string is pinned here - including for enums the examples never show.
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
}
