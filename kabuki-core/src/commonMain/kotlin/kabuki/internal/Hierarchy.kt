package kabuki.internal

import androidx.compose.ui.test.isRoot
import kabuki.KabukiTestScope
import kabuki.KabukiUsageError

/** Compose's wording for "this scene composed nothing"; matched as a substring. */
private const val MISSING_HIERARCHY = "No compose hierarchies found"

/**
 * Rewrites Compose's missing-hierarchy error into one that names the fix.
 * Returns null for anything else, so callers can pass any failure through.
 *
 * The type matters as much as the text: Compose raises this as an
 * IllegalStateException, while a failed assertion carrying those words is a test
 * reading that string off the screen - not ours to rewrite.
 */
internal fun emptySceneErrorOrNull(error: Throwable?): KabukiUsageError? {
    if (error !is IllegalStateException || error.message?.contains(MISSING_HIERARCHY) != true) {
        return null
    }
    return KabukiUsageError(
        message = "Nothing is composed on screen, so nothing here can pass.\n$ADVICE\n" +
            "Compose says: ${error.message}",
        cause = error,
    )
}

/** Android in practice: a desktop scene exists from the moment the runner creates it. */
private val ADVICE =
    """
    Two causes Compose knows nothing about, both in the build script:
      - androidx.compose.ui:ui-test-manifest missing from debugImplementation, so there is no activity to launch;
      - the instrumented APK targeting minSdk, which keeps a modern Android in compatibility mode:
        android { testOptions { targetSdk = ... } }.
    """.trimIndent()

/**
 * Fails unless something is composed.
 *
 * Absence proves nothing in an empty scene: on API 36, 32 of 93 tests "passed"
 * while the app was never on screen - all of them asserting that something is NOT
 * there.
 */
internal fun KabukiTestScope.requireLiveHierarchy() {
    // Dialogs own roots of their own, so several is normal and zero is not. The
    // flag is what turns zero into an error - passed explicitly because it is the
    // reason for the call.
    context.onAllNodes(isRoot(), useUnmergedTree = false)
        .fetchSemanticsNodes(atLeastOneRootRequired = true)
}
