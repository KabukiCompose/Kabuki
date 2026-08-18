# R8 rules for the instrumented TEST apk.
#
# It has to stay minified: AGP shrinks it with the app's mapping, so turning
# shrinking off breaks every reference to an obfuscated app class.

-dontwarn javax.lang.model.element.**
-dontwarn com.google.errorprone.annotations.**

# Nothing references a test class statically - without this R8 removes them all and
# the runner reports "Starting 0 tests", a run that checked nothing.
-keepclasseswithmembers class * {
    @org.junit.Test <methods>;
}
-keep class * extends org.junit.runner.Runner
-keep class * extends org.junit.runner.notification.RunListener

# No keep rules for Kabuki page objects on purpose - those must come from
# kabuki-core's own consumer-rules.pro.
