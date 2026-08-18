package kabuki.semantics

/**
 * A constant with a body compiles into an anonymous subclass whose simple name is
 * the CONSTANT's name, so `this::class` cannot be trusted here. Such a subclass
 * reports `isEnum == false` and has the enum type as its superclass.
 */
internal actual val Enum<*>.declaringEnumName: String
    get() {
        val type = javaClass
        return (if (type.isEnum) type else type.superclass).simpleName
    }
