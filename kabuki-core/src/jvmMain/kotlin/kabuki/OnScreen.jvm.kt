package kabuki

import kotlin.reflect.KClass

/**
 * JVM: instantiated reflectively through the no-arg constructor. If the test
 * module is minified, keep those constructors - see `consumer-rules.pro`.
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
