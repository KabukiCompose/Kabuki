# R8 rules for the minified test run (-Pkabuki.minifiedTests).

# THE RULE THIS FILE EXISTS FOR: Kabuki builds a tag as "EnumSimpleName.ENTRY" at
# runtime, so obfuscation breaks it. Measured: PlaybillTags -> z3.k.
-keepnames class kabuki.sample.ui.**Tags

# The rest is not about Kabuki. The test apk shares the app's process and resolves
# classes out of it, so R8 must not drop what the tests need.
-dontwarn javax.lang.model.element.**
-dontwarn com.google.errorprone.annotations.**
# AndroidJUnitRunner.onCreate; without it the run reports "0 tests".
-keep class androidx.tracing.Trace { *; }
-keep class kotlin.LazyKt { *; }
-keep class kotlin.Lazy { *; }
# Referenced from the <clinit> of EVERY Kotlin enum, `entries` used or not.
-keep class kotlin.enums.** { *; }

# No keep rule gets a fully green run here: the cascade of inlined and dropped
# Compose/Kotlin members has no end (measured, eight rounds). What this variant is
# good for is building the app and reading mapping.txt.
