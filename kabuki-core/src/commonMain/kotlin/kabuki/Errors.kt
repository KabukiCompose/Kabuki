package kabuki

/**
 * Failure of a Kabuki operation: node description, the last underlying error and
 * a semantics tree dump (see [KabukiConfig.dumpSemanticsTreeOnFailure]).
 *
 * An AssertionError so frameworks report a failed assertion, not a crash.
 */
public class KabukiAssertionError(message: String, cause: Throwable? = null) : AssertionError(message, cause)

/**
 * The library is used in a way that cannot work - a page object bound to no test,
 * a singleton component inside two scoping owners.
 *
 * Retry lets this type through instead of waiting out the timeout: the UI is not
 * late, the description of it is wrong.
 */
public class KabukiUsageError(message: String) : IllegalStateException(message)
