package kabuki.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kabuki.sample.data.TheaterRepository
import kabuki.sample.model.Performance
import kabuki.sample.model.Seat
import kabuki.sample.model.Ticket
import kabuki.semantics.testListItem
import kabuki.semantics.testListLength
import kabuki.semantics.testTag
import kotlinx.coroutines.launch

enum class SeatTags {
    DIALOG,
    CLOSE_BUTTON,
    LOADER,
    LIST,

    /** + "row_number" */
    SEAT,
}

enum class ConfirmTags {
    DIALOG,
    BUY_BUTTON,
    CANCEL_BUTTON,
    BUY_PROGRESS,
}

/**
 * Full-height seat picker dialog: fills the whole screen, rows scroll inside a
 * LazyColumn. On top of it - a small purchase confirmation AlertDialog with a
 * progress indicator on the button.
 */
@Composable
fun SeatPickerDialog(
    performance: Performance,
    repository: TheaterRepository,
    onDismiss: () -> Unit,
    onBought: (Ticket) -> Unit,
) {
    var seats by remember { mutableStateOf<List<Seat>?>(null) }
    LaunchedEffect(performance.id) {
        seats = repository.loadSeats(performance.id)
    }

    var pendingSeat by remember { mutableStateOf<Seat?>(null) }
    var buying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag(SeatTags.DIALOG)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Choose a seat", style = MaterialTheme.typography.headlineSmall)
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag(SeatTags.CLOSE_BUTTON),
                    ) {
                        Text("Close")
                    }
                }
                Text(
                    text = performance.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )

                val loaded = seats
                if (loaded == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(SeatTags.LOADER))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .testTag(SeatTags.LIST)
                            .testListLength(TheaterRepository.ROWS),
                    ) {
                        items(TheaterRepository.ROWS) { rowIndex ->
                            val row = rowIndex + 1
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.testListItem(rowIndex),
                            ) {
                                Text(
                                    text = "Row $row",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    loaded.filter { it.row == row }.forEach { seat ->
                                        FilledTonalButton(
                                            onClick = { pendingSeat = seat },
                                            enabled = !seat.taken,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag(SeatTags.SEAT, seat.row, seat.number),
                                        ) {
                                            Text("${seat.number}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val seatToBuy = pendingSeat
    if (seatToBuy != null) {
        AlertDialog(
            onDismissRequest = { if (!buying) pendingSeat = null },
            modifier = Modifier.testTag(ConfirmTags.DIALOG),
            title = { Text("Confirm purchase") },
            text = {
                Text(
                    "\"${performance.title}\", row ${seatToBuy.row}, seat ${seatToBuy.number} - " +
                        "¥${performance.price}",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            buying = true
                            val ticket = repository.buyTicket(performance, seatToBuy)
                            buying = false
                            pendingSeat = null
                            onBought(ticket)
                        }
                    },
                    enabled = !buying,
                    modifier = Modifier.testTag(ConfirmTags.BUY_BUTTON),
                ) {
                    if (buying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp).testTag(ConfirmTags.BUY_PROGRESS),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Buy")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingSeat = null },
                    enabled = !buying,
                    modifier = Modifier.testTag(ConfirmTags.CANCEL_BUTTON),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
