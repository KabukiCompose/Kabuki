# Kabuki

UI tests for Compose Multiplatform. One test, written once, runs on desktop
(headless, no emulator) and on an Android device.

> **Status: early development.** The API changes without notice and nothing is
> published to Maven Central yet. Build locally with `./gradlew publishToMavenLocal`.

## What a test looks like

Tag the screen in production code - the tag is an enum, and parameters stay
parameters instead of being glued into a string:

```kotlin
enum class PlaybillTags { SCREEN, LIST, CARD, CARD_TITLE, CARD_PRICE }

@Composable
fun PlaybillScreen(modifier: Modifier = Modifier, performances: List<Performance>) {
    LazyVerticalGrid(
        // Publishes the full length, so a test can assert it without
        // scrolling the whole list into composition.
        modifier = modifier.testTag(PlaybillTags.LIST).testListLength(performances.size),
    ) {
        itemsIndexed(performances) { index, performance ->
            Card(
                modifier = Modifier
                    .testTag(PlaybillTags.CARD, performance.id)
                    // Lets the test address items by index, composed or not.
                    .testListItem(index),
            ) { ... }
        }
    }
}
```

`testTag`, `testListLength` and `testListItem` come from `kabuki-semantics` -
the only module that goes into the application.

Describe the screen once:

```kotlin
class PlaybillScreen : Screen<PlaybillScreen>() {
    override val root = node(PlaybillTags.SCREEN)

    val cards = lazyList(PlaybillTags.LIST) { itemType(::PerformanceCardItem) }

    fun card(id: String) = node(PlaybillTags.CARD, id)
}

class PerformanceCardItem(scope: ListItemScope) : ListItem(scope) {
    val title = child(PlaybillTags.CARD_TITLE)
    val price = child(PlaybillTags.CARD_PRICE)
}
```

Write the test in `commonTest` - it runs everywhere:

```kotlin
@Test
fun buyTicket() = runKabukiTest(name = "Buy a ticket") {
    setContent { TheaterApp(state) }

    step("The playbill is loaded") {
        onScreen<PlaybillScreen> {
            cards.assertLengthEquals(6)
            cards.firstItem<PerformanceCardItem> {
                title.assertTextContains("Chushingura")
            }
            card("chushingura").click()
        }
    }
}
```

Steps and page objects are optional. The short form works too:

```kotlin
@Test
fun simple() = runKabukiTest {
    setContent { App() }
    node(PlaybillTags.LIST).assertIsDisplayed()
}
```

## Why

Espresso, Kaspresso and KakaoCup Compose only run on Android. When an app is
built with Compose Multiplatform and ships on both Android and desktop, its
tests have to be written twice. Kabuki exists so the same test covers both.

## What it does

- **Retry on every operation** - assertions wait for the UI instead of
  snapshotting it, so no manual `waitUntil` around each check.
- **Enum tags with parameters** - `testTag(SEAT, row, number)` instead of
  string concatenation. A typo is a compile error, and mixed-up arguments are
  reported with the list of nodes carrying that tag.
- **Lazy lists and grids with typed items** - address items by index, assert
  the full length, not just the visible part.
- **Steps and scenarios in the core** - numbered `1`, `1.1`, `1.2`, any depth,
  reported through the listener SPI on every platform.
- **Semantics tree dump in the failure message** - what was actually on screen
  at the moment of failure.
- **A real window next to the headless scene** on desktop, so a test can be
  watched. No `java.awt.Robot`, so windows do not fight over the cursor.
- **Environment profiles** - scene size, density, window size class,
  `os()` branches and `assumeOs` / `assumeSizeClass`.
- **Operation interceptors** - replace *how* an operation is performed
  (see `ClickOnUiThread`, `ClickViaSemanticsAction`) instead of every test
  carrying a workaround.
- **Incremental adoption** - `kabuki-junit4` works on top of an existing
  `ComposeTestRule`, so new tests can live next to the old ones.

## Modules

| module | what for | goes into |
|---|---|---|
| `kabuki-semantics` | test tags on `Modifier` | **production code** |
| `kabuki-core` | node API, DSL, retry, lists | tests |
| `kabuki-runner` | desktop and Android runners, `runKabukiTest` | tests |
| `kabuki-junit4` | Kabuki over a foreign `ComposeTestRule` | tests |

Only `kabuki-semantics` is linked into the application itself, and it carries
nothing but the tag helpers.

## Requirements

Kotlin 2.4, Compose Multiplatform 1.11, JVM 11 bytecode.

## License

[Apache 2.0](LICENSE)
