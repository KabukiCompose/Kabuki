---
name: migrate-to-kabuki
description: >
  Migrate an existing Compose UI test suite to Kabuki (scoped page-object DSL for
  Compose Multiplatform). Supports migration from KakaoCup Compose, Ultron Compose,
  raw compose-ui-test (createComposeRule/runComposeUiTest + onNodeWithTag), and
  hand-rolled Robot-pattern tests. Use when asked to "migrate to kabuki",
  "convert tests to kabuki", "переведи тесты на kabuki", or when adopting Kabuki
  in a project with existing Compose UI tests.
---

# Migrate Compose UI tests to Kabuki

You are migrating an existing Compose UI test suite to Kabuki. Work file by file,
keep tests green after every batch, never change what a test VERIFIES - only how
it is written.

## Step 0: Inventory

1. Detect the source framework(s) by imports:
   - `io.github.kakaocup.compose` -> KakaoCup Compose
   - `com.kaspersky.kaspresso` (+ kakaocup compose screens) -> Kaspresso
   - `com.atiurin.ultron` -> Ultron
   - `androidx.compose.ui.test.junit4` / `createComposeRule` / bare `onNodeWithTag` -> raw compose-ui-test
   - Classes named `*Robot` with chained methods over a test rule -> Robot pattern
   - `.yaml` flows with `tapOn`/`assertVisible` -> Maestro (draft conversion only)
2. Count test classes, page objects/robots, custom matchers, custom wait helpers.
3. Choose a strategy by project size:
   - SMALL (< ~15 test classes): full migration in batches, drop the old
     framework at the end.
   - LARGE or has its own base test class / own activity rule: INCREMENTAL -
     add `kabuki-junit4`, wire the mixin into the existing base class,
     then migrate screen by screen. The old framework and Kabuki coexist over
     the same ComposeTestRule:
     ```kotlin
     abstract class BaseTest : /* existing supertype, */ KabukiInterop {
         @get:Rule(order = 0) val composeRule = createAndroidComposeRule<MainActivity>()
         @get:Rule(order = 1) val kabukiRule = KabukiRule(composeRule)
         override val kabukiScope get() = kabukiRule.kabukiScope
     }
     // in tests, FLAT - the compiler picks the framework by screen type:
     // onScreen<OldKakaoScreen>(composeRule) { ... }   // legacy stays as is
     // onScreen<NewKabukiScreen> { ... }                // migrated/new parts
     ```
     PREFER `KabukiRule` over `kabukiScope by lazy { }`: only a rule knows where
     the test begins and ends, so reporting sees a test (not loose steps), the
     test name comes from JUnit, and page objects are released afterwards.
     Declare it INSIDE the Compose rule - `order = 1` against `order = 0`, since
     a higher order means inner.

     One-shot calls without a base class: `composeRule.onScreen<T> { }` or
     `composeRule.kabuki(name = "...") { step(...) { ... } }` - the block form
     reports the test too.
4. Present the migration plan (strategy + batches of related screens) before editing.

## Step 1: Dependencies

Add to the test source set (versions: use the latest published kabuki):

```kotlin
// ONE dependency covers desktop and Android - declare it in commonTest, not in
// the platform source sets (KMP inherits downwards; declaring it per platform
// leaves commonTest without the API and the IDE shows unresolved references).
implementation("io.github.kabukicompose:kabuki-runner:<version>")
// interop layer, only while migrating on top of someone else's ComposeTestRule
implementation("io.github.kabukicompose:kabuki-junit4:<version>")
// production code, ONLY if adopting enum tags / list semantics
implementation("io.github.kabukicompose:kabuki-semantics:<version>")
```

Do NOT remove the old framework until the last batch is green.

## Packages to import from

- `kabuki` - what the test body uses: `KabukiTestScope`, config, `os`/`assume*`,
  `Scenario`, profiles;
- `kabuki.page` - what a page object is built from: `Screen`, `Component`,
  `UiNode`, `LazyList`, `ListItem`, `onScreen`;
- `kabuki.listener` - reporting SPI: `KabukiListener`, `ConsoleListener`, event
  and result types;
- `kabuki.runner` - what starts a test: `runKabukiTest`, `KabukiTestCase`;
- `kabuki.semantics` - the only package production code touches: `testTag`,
  `testListItem`, `testListLength`.

So a migrated page object imports `kabuki.page.Screen`, the test imports
`kabuki.page.onScreen`, and its base class comes from `kabuki.runner`.

## Step 2: API mapping tables

### From KakaoCup Compose

| KakaoCup | Kabuki |
|---|---|
| `ComposeScreen<T>(semanticsProvider, viewBuilderAction)` | `Screen<T>()` + `override val root = node { ... }` - no provider at all |
| `val btn: KNode = child { hasTestTag("x") }` | `val btn = node { withTag("x") }` |
| `onComposeScreen<T>(composeTestRule) { }` | `onScreen<T> { }` (waits for root with retry - drop manual waits before it) |
| `btn { performClick() }` | `btn { click() }` |
| `performTextInput("a")` / `performTextReplacement` / `performTextClearance` | `typeText("a")` / `replaceText` / `clearText` |
| `assertIsDisplayed()` etc. | same names, but retried until timeout (delete surrounding `waitUntil`/idling workarounds) |
| `createComposeRule()` + `@get:Rule` | `runDesktopTest { }` / `runAndroidTest { }` - rule -> function, delete the rule |
| `KLazyListNode` + `itemType(::Item)` + `childAt<Item>(n)` | `lazyList(TAG) { itemType(::Item) }` + `itemAt<Item>(n)` (scrolls itself) |
| `KLazyListItemNode` subclass with `child { }` | `ListItem(scope)` subclass with `child(TAG)` / `child { }` |
| `lazyListItemPosition(n)` modifier (custom) | `Modifier.testListItem(n)` from kabuki-semantics |
| `intercept { }` | observing: `config = { listeners += ... }`; changing HOW an operation runs: `config = { interceptors += ... }` - a `KabukiInterceptor` wraps the call inside the retry, see `ClickViaSemanticsAction` |
| `useUnmergedTree` in screen constructor | nothing - drop it. Kabuki picks the tree per search (tags unmerged, text merged); a single node overrides with `.merged` / `.unmerged`. Only a suite that relied on global unmerged everywhere needs `config = { treeStrategy = TreeStrategy.AlwaysUnmerged }`, and that is a migration crutch to remove later |
| `KakaoComposeTestRule` global config | `config = { }` block per test or a project base class |

Behavioral differences to announce in the final report:
- Every Kabuki operation retries until timeout - tests that were flaky may start
  passing; manual `waitUntil`/`waitForIdle` calls become dead code, remove them.
- `onScreen` waits for the root - leading `assertIsDisplayed()` on the first
  element becomes redundant.
- Failure messages include expected/actual and a semantics tree dump.

### From Kaspresso (Compose support)

Kaspresso's Compose screens ARE KakaoCup screens - apply the KakaoCup table for
all screen/node code. On top of that:

| Kaspresso | Kabuki |
|---|---|
| `run { step("...") { } }` | `step("...") { }` (numbering built in) |
| `scenario(MyScenario())` | `scenario(MyScenario())` - Scenario is a fun interface |
| flaky-safety interceptors / `flakySafely { }` | DELETE - every Kabuki operation retries by design |
| Kaspresso logging interceptors | `config = { listeners += ... }` (KabukiListener) |
| `before { } after { }` sections | `KabukiTestCase.beforeTest/afterTest` or plain code |

### From Maestro YAML (draft mode)

Maestro flows convert to a DRAFT only - always tell the user the result needs
manual review (Maestro drives a real app; Kabuki drives a composable):
`tapOn: "text"` -> `nodeWithText("text").click()`; `tapOn: id:` ->
`node(tag).click()`; `assertVisible` -> `assertIsDisplayed()`;
`inputText` -> `typeText`. Collect all selectors per flow and generate one
Screen class skeleton per screen the flow visits.

### From Ultron Compose

| Ultron | Kabuki |
|---|---|
| `hasTestTag("x").click()` (flat extension style) | declare in a Screen: `val x = node { withTag("x") }` then `x.click()` |
| `hasText("y").assertIsDisplayed()` | `nodeWithText("y").assertIsDisplayed()` or a Screen property |
| `runUltronUiTest { }` | `runDesktopTest { }` / `runAndroidTest { }` |
| `UltronComposeConfig.operationTimeoutMs` | `config = { defaultTimeout = ... }` |
| `withTimeout(...)` on an operation | `node.withTimeout(...)` |
| `UltronComposeList(matcher)` + `getItem<T>(n)` | `lazyList(TAG) { itemType(::T) }` + `itemAt<T>(n)` |
| Ultron listeners (`UltronCommonConfig.addListener`) | `config = { listeners += object : KabukiListener { ... } }` |
| `isSuccess { ... }` (any operation as a Boolean) | `.passed { ... }` |
| `execute { }` / `perform { }` (Ultron extension) | `.read("name") { }` / `.action("name") { }` |
| `withAssertion("desc") { ... }.click()` | `.clickUntil("desc") { ... }`, or `.withAssertion("desc") { ... }` for other operations |
| `UltronComposeList.getItem(matcher)` (item by content) | `itemWhere<T>({ withText("...") }) { }` / `itemNodeWhere { withText("...") }` |

Ultron is flat (no page objects required); when migrating, GROUP the flat
matchers into Screen classes per logical screen - propose the grouping first.

### From raw compose-ui-test

| Raw | Kabuki |
|---|---|
| `createComposeRule()` / `createAndroidComposeRule` | `runDesktopTest` / `runAndroidTest` |
| `composeTestRule.setContent { }` | `setContent { }` inside the runner block |
| `onNodeWithTag("x")` | `node("x")` (or a Screen property) |
| `onNodeWithText("y")` | `nodeWithText("y")` |
| `onNode(matcher)` | `node { matching(matcher) }` |
| `onAllNodesWithTag("x")` | `nodeAll("x")` |
| `.performClick()` / `.performTextInput` | `.click()` / `.typeText` |
| `.assertIsDisplayed()` etc. | same names, retried |
| `.performSemanticsAction(...)` or any interaction with no DSL equivalent | `.action("name") { it.performSemanticsAction(...) }` - keeps retry and the report |
| reading semantics by hand (`fetchSemanticsNode().config[...]`) | `.read("name") { ... }` - same, and null is a valid result |
| `try`/`runCatching` around an assert to branch on it | `.passed { assertIsDisplayed() }` |
| `composeTestRule.waitUntil { ... }` | usually DELETE - the following assert retries by itself |
| `mainClock.advanceTimeBy` hacks | usually DELETE - retry advances the clock |
| repeated tag strings | introduce enum tags + `Modifier.testTag(enum)` from kabuki-semantics (optional but recommended) |

Raw tests have no page objects: propose extracting Screen classes when 3+ tests
touch the same tags; otherwise a mechanical 1:1 conversion is fine.

### From Robot pattern (hand-rolled)

Robots map almost 1:1 to Screens: robot class -> `Screen<T>`, robot method ->
screen method or scenario. Chained returns (`fun clickLogin(): LoginRobot`) ->
plain Unit methods (Kabuki scoping replaces chaining). The robot's rule/provider
field is deleted - Kabuki screens are context-free.

## Step 3: Migration order (per batch)

1. Migrate page objects/robots of a batch first, then their tests.
2. Replace the runner (rule -> `runDesktopTest`/`runAndroidTest` function).
3. Delete dead waits (`waitUntil`, `Thread.sleep`, idling resources) - Kabuki
   retries every operation. NEVER delete a wait that guards non-UI state
   (e.g. polling a fake server) - convert those to plain loops or keep them.
4. Compile, run the migrated batch, fix, then proceed to the next batch.
5. NEVER change step("...") texts or test names without being asked.

## Step 4: Final report

Summarize: files migrated, tests passing before/after, deleted wait-hacks count,
behavioral differences the team should know (retry semantics, onScreen root wait),
and anything left with a TODO (framework-specific features without a Kabuki
equivalent yet).
