package kabuki.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kabuki.sample.data.TheaterRepository
import kabuki.sample.model.Ticket

sealed interface TheaterScreen {
    data object Playbill : TheaterScreen
    data class PerformanceDetails(val performanceId: String) : TheaterScreen
    data object Tickets : TheaterScreen
}

/**
 * Root application state. Hoisted above the composables - in Kabuki tests this is
 * the environment: a test can see purchased tickets and the current screen directly.
 */
class TheaterState(
    val repository: TheaterRepository = TheaterRepository(),
) {
    var screen: TheaterScreen by mutableStateOf(TheaterScreen.Playbill)
    val tickets = mutableStateListOf<Ticket>()
}

/**
 * Shared by both platform entry points. The `remember` belongs here rather than in
 * androidMain: Android Lint analyses only the android source set, so a type declared
 * in commonMain reads as Unit there and RememberReturnType fires on a correct call.
 */
@Composable
fun rememberTheaterState(): TheaterState {
    return remember { TheaterState() }
}
