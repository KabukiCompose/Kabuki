package kabuki

import kotlin.reflect.KClass

/**
 * Android: instantiated reflectively through the no-arg constructor. R8 strips
 * unused constructors, so a minified test build needs the keep rule shipped in
 * `consumer-rules.pro`.
 */
public actual fun <T : Screen<T>> instantiateScreen(kClass: KClass<T>): T {
    try {
        return kClass.java.getDeclaredConstructor().newInstance()
    } catch (e: NoSuchMethodException) {
        throw IllegalArgumentException(
            "Screen ${kClass.simpleName} must have a public no-arg constructor " +
                "to be used with onScreen<${kClass.simpleName}> { }. " +
                "For parameterized screens use onScreen(instance) { }.",
            e,
        )
    }
}
