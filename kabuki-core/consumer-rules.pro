# Kabuki core - consumer rules.
#
# onScreen<T>() instantiates screens reflectively through a public no-arg
# constructor (see instantiateScreen). If the module that contains the tests
# is minified, R8 may strip those constructors.
-keep class * extends kabuki.page.Screen {
    public <init>();
}
-keep class * extends kabuki.page.Component {
    public <init>();
}

# A page object declared as `object` is reached through its INSTANCE field. Losing
# it would send instantiateScreen down the constructor path and produce a SECOND
# instance of a singleton - two page objects where the test assumes one. Kabuki
# refuses to do that, so without this rule such a test fails outright.
-keepclassmembers class * extends kabuki.page.Screen {
    public static ** INSTANCE;
}
-keepclassmembers class * extends kabuki.page.Component {
    public static ** INSTANCE;
}

# Skipping (assumeOs / assumeSizeClass) builds the framework's own "assumption
# failed" exception BY REFLECTION - kabuki-core depends on no test framework.
# Under a minified test build those names must survive, or every skip silently
# becomes a failure. Harmless when the class is absent: the rule matches nothing.
-keepnames class org.junit.AssumptionViolatedException
-keepnames class org.opentest4j.TestAbortedException
