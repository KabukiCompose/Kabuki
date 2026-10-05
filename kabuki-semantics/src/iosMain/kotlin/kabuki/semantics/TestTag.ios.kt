package kabuki.semantics

/**
 * A constant with a body compiles into an anonymous subclass, and its `simpleName`
 * is the CONSTANT's - the same trap as on the JVM, minus the `isEnum` flag that
 * detects it there.
 *
 * `qualifiedName` keeps the enum either way: a plain constant reports the enum
 * itself (`pkg.Tags`), a bodied one reports the subclass under it
 * (`pkg.Tags.Tags$SCREEN`). So the last segment, cut at the `$`, is the enum in
 * both cases - nested enums included.
 */
internal actual val Enum<*>.declaringEnumName: String
    get() {
        val kClass = this::class
        val qualified = kClass.qualifiedName ?: return kClass.simpleName ?: "Enum"
        return qualified.substringAfterLast('.').substringBefore('$')
    }
