package kabuki.page

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * Android: an `object` screen is taken as it is, a class is created through its
 * no-arg constructor. R8 strips unused constructors, so a minified test build
 * needs the keep rule shipped in `consumer-rules.pro` - an `object` needs no rule
 * at all, it is referenced by name.
 */
public actual fun <T : Screen<T>> instantiateScreen(kClass: KClass<T>): T {
    objectInstanceOrNull(kClass)?.let { instance -> return instance }

    val constructor = try {
        kClass.java.getDeclaredConstructor()
    } catch (e: NoSuchMethodException) {
        throw IllegalArgumentException(
            "Screen ${kClass.simpleName} must have a no-arg constructor " +
                "to be used with onScreen<${kClass.simpleName}> { }. " +
                "For parameterized screens use onScreen(instance) { }.",
            e,
        )
    }
    // A non-public constructor means an `object` (or a deliberate singleton) whose
    // INSTANCE field could not be read - stripped by R8, most likely. Creating it
    // here would hand out a SECOND instance of a singleton: bindings and scoping
    // would land on a different object than `PlaybillScreen { }` uses, and nothing
    // would look broken until much later.
    if (!Modifier.isPublic(constructor.modifiers)) {
        throw IllegalArgumentException(
            "Screen ${kClass.simpleName} has no readable INSTANCE field and only a non-public " +
                "constructor. If it is an `object`, its INSTANCE field is missing (minified " +
                "away?) - keep it, see kabuki-core/consumer-rules.pro. Creating a second " +
                "instance of a singleton is not something Kabuki will do silently.",
        )
    }
    makeAccessible(constructor)
    try {
        return constructor.newInstance()
    } catch (e: IllegalAccessException) {
        throw IllegalArgumentException(
            "Screen ${kClass.simpleName} is not accessible to Kabuki. Declare it public or " +
                "internal, or enter it with onScreen(${kClass.simpleName}()) { }.",
            e,
        )
    }
}

/**
 * The singleton behind a Kotlin `object`, or null for a normal class.
 *
 * Read from the static INSTANCE field rather than through kotlin-reflect: an
 * `object` has a PRIVATE constructor, so the reflective path would fail with an
 * IllegalAccessException that says nothing about what to do.
 */
@Suppress("UNCHECKED_CAST")
private fun <T : Screen<T>> objectInstanceOrNull(kClass: KClass<T>): T? {
    val value = runCatching {
        val field = kClass.java.getDeclaredField("INSTANCE")
        makeAccessible(field)
        field.get(null)
    }.getOrNull()
    // isInstance, not a bare cast: the cast is erased, so a class that happens to
    // have an INSTANCE field of some other type would be returned as a screen and
    // blow up much later, somewhere unrelated.
    return if (kClass.java.isInstance(value)) value as T else null
}

/**
 * A page object declared `private` in a test file is package-private in the
 * bytecode - visible to the test, not to this library. Opening it up keeps that
 * ordinary way of writing tests working.
 */
private fun makeAccessible(member: AccessibleObject) {
    runCatching { member.isAccessible = true }
}
