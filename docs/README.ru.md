# Kabuki

[English version](../README.md)

UI-тесты для Compose Multiplatform. Один тест, написанный один раз, идёт и на
десктопе (headless, без эмулятора), и на Android-устройстве.

> **Статус: ранняя разработка.** API меняется без предупреждения, на Maven
> Central ничего ещё не опубликовано - см. [Подключение](#подключение).

## Как выглядит тест

Разметка живёт в прод-коде. Тег - enum, а параметры остаются параметрами,
вместо склейки в строку:

```kotlin
enum class PlaybillTags { SCREEN, LIST, CARD, CARD_TITLE, CARD_PRICE }

@Composable
fun PlaybillScreen(modifier: Modifier = Modifier, performances: List<Performance>) {
    LazyVerticalGrid(
        // Публикует полную длину - тест проверит её, не прокручивая
        // весь список в композицию.
        modifier = modifier.testTag(PlaybillTags.LIST).testListLength(performances.size),
    ) {
        itemsIndexed(performances) { index, performance ->
            Card(
                modifier = Modifier
                    .testTag(PlaybillTags.CARD, performance.id)
                    // Позволяет обращаться к элементу по индексу,
                    // независимо от того, скомпонован он или нет.
                    .testListItem(index),
            ) { ... }
        }
    }
}
```

`testTag`, `testListLength` и `testListItem` - из `kabuki-semantics`,
единственного модуля, который попадает в приложение.

Экран описывается один раз:

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

Тест пишется в `commonTest` и идёт везде:

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

Шаги и page objects - по желанию. Короткая форма тоже работает:

```kotlin
@Test
fun simple() = runKabukiTest {
    setContent { App() }
    node(PlaybillTags.LIST).assertIsDisplayed()
}
```

## Зачем

Espresso, Kaspresso и KakaoCup Compose работают только на Android. Если
приложение собрано на Compose Multiplatform и выходит и на Android, и на
десктопе, тесты приходится писать дважды. Kabuki существует, чтобы один тест
закрывал обе платформы.

## Что умеет

- **Retry на каждой операции** - проверки ждут UI, а не делают снимок, поэтому
  вокруг каждой из них не нужен свой `waitUntil`.
- **Enum-теги с параметрами** - `testTag(SEAT, row, number)` вместо склейки
  строк. Опечатка становится ошибкой компиляции, а перепутанные аргументы
  попадают в диагностику вместе со списком узлов с этим тегом.
- **Lazy-списки и гриды с типизированными элементами** - обращение по индексу,
  проверка полной длины, а не только видимой части.
- **Шаги и сценарии в ядре** - нумерация `1`, `1.1`, `1.2` на любой глубине,
  доступная через SPI слушателей на всех платформах.
- **Дамп дерева semantics в сообщении об ошибке** - что реально было на экране
  в момент падения.
- **Сторожевой таймер** - заблокировавшийся платформенный вызов прервать нельзя,
  но прогон больше не висит в тишине: Kabuki сообщает, какая операция и на каком
  узле встала - в консоль или в ваш логгер.
- **Живое окно рядом с headless-сценой** на десктопе, чтобы за тестом можно было
  наблюдать. Без `java.awt.Robot`, поэтому окна не воюют за курсор.
- **Профили окружения** - размер сцены, плотность, класс размера окна, развилки
  `os()` и `assumeOs` / `assumeSizeClass`.
- **Точки расширения вместо тупика** - `action` и `read` выполняют ваш код с тем
  же retry и отчётом, что и встроенная операция, `passed` отвечает вместо того
  чтобы валить тест, `raw` остаётся на самый край.
- **Операции, ждущие своего эффекта** - `clickUntil("the dialog opens") { ... }`
  повторяется внутри одного retry, для случая "нажатие не дошло".
- **Элементы списка по содержимому** - `itemNodeWhere { withText("Anna") }`
  прокручивает список до нужного элемента и дальше работает с ним по индексу:
  содержимое меняется, номера нет.
- **Перехватчики операций** - меняют то, *как* операция выполняется
  (`ClickViaSemanticsAction`, а на десктопе ещё `ClickOnUiThread`), вместо
  костыля в каждом тесте.
- **Постепенное внедрение** - `kabuki-junit4` работает поверх существующего
  `ComposeTestRule`, поэтому новые тесты живут рядом со старыми.

## Модули

| модуль | для чего | попадает |
|---|---|---|
| `kabuki-semantics` | теги на `Modifier` | **в прод-код** |
| `kabuki-core` | node-API, DSL, retry, списки | в тесты |
| `kabuki-runner` | раннеры десктопа и Android, `runKabukiTest` | в тесты |
| `kabuki-junit4` | Kabuki поверх чужого `ComposeTestRule` | в тесты |

В само приложение линкуется только `kabuki-semantics`, и в нём нет ничего кроме
хелперов для тегов.

## Подключение

На Maven Central пока ничего нет, поэтому ставится из клона:

```bash
git clone https://github.com/KabukiCompose/Kabuki
cd Kabuki && ./gradlew publishToMavenLocal
```

Дальше - в модуле с тестами:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.kabukicompose:kabuki-semantics:0.1.0-SNAPSHOT")
        }
        commonTest.dependencies {
            implementation("io.github.kabukicompose:kabuki-runner:0.1.0-SNAPSHOT")
        }
    }
}
```

Для Android нужно ещё несколько строк, и одну из них легко пропустить: без неё
каждый тест падает с `No compose hierarchies found in the app`, и это сообщение
ничего не говорит про две настройки, которые к нему приводят. Kabuki называет их
в тексте падения; вся настройка Android - в [Setup and pitfalls](setup.md).

## Требования

Kotlin **2.2** или новее, Compose Multiplatform 1.11, байткод JVM 11.

Собирается более новым компилятором, но артефакты несут metadata 2.2 и зависят
от stdlib 2.2 намеренно: тестовая библиотека не вправе заставлять обновлять
Kotlin.

## Лицензия

[Apache 2.0](../LICENSE)

Имя и логотип Kabuki лицензией Apache 2.0 не покрываются.
