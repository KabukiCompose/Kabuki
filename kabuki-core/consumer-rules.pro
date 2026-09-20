# Kabuki core - consumer rules.
#
# Skipping (assumeOs / assumeSizeClass) builds the framework's own "assumption
# failed" exception BY REFLECTION - kabuki-core depends on no test framework.
# Under a minified test build those names must survive, or every skip silently
# becomes a failure. Harmless when the class is absent: the rule matches nothing.
-keepnames class org.junit.AssumptionViolatedException
-keepnames class org.opentest4j.TestAbortedException
