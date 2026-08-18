package kabuki

/**
 * Failure of a Kabuki operation: node description, the last underlying error and
 * a semantics tree dump (see [KabukiConfig.dumpSemanticsTreeOnFailure]).
 *
 * An AssertionError so frameworks report a failed assertion, not a crash.
 */
public class KabukiAssertionError(message: String, cause: Throwable? = null) : AssertionError(message, cause)

/**
 * The test cannot work as written - a page object bound to no test, a singleton
 * component inside two scoping owners, a scene that composed nothing at all.
 *
 * Retry lets this type through instead of waiting out the timeout: the UI is not
 * late, the description of it (or the setup around it) is wrong.
 */
public class KabukiUsageError(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
