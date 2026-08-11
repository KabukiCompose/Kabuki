# Kabuki semantics - consumer rules.
#
# This is the only Kabuki artifact linked into PRODUCTION code, so it ships
# with the smallest possible footprint: the library itself needs no keep rules
# (no reflection; SemanticsPropertyKey names are string literals that R8 does
# not touch).
#
# IMPORTANT for teams running UI tests against a MINIFIED build:
# test tags are built as "EnumSimpleName.ENTRY" (see tagName). R8 renames
# classes, so a minified build emits "a.SCREEN" while the (non-minified) test
# code still looks for "PlaybillTags.SCREEN" - every tag lookup fails.
#
# Keep the names of YOUR tag enums in that case, e.g.:
#   -keepnames class com.myapp.**Tags
# or, bluntly, every enum in the app:
#   -keepnames class * extends java.lang.Enum
#
# Kabuki deliberately forces neither rule on all consumers: which enums exist
# is an application-level decision, and a blanket -keepnames would bloat every
# app that never runs tests against a minified build.
