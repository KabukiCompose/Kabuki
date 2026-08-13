package kabuki.sample

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kabuki.sample.ui.TheaterApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kabuki Theater",
        state = rememberWindowState(size = DpSize(1100.dp, 800.dp)),
    ) {
        TheaterApp(rememberTheaterState())
    }
}
