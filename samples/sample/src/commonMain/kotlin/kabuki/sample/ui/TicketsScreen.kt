package kabuki.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kabuki.sample.TheaterState
import kabuki.semantics.testListItem
import kabuki.semantics.testListLength
import kabuki.semantics.testTag

enum class TicketsTags {
    SCREEN,
    EMPTY,
    LIST,

    /** + ticket index */
    CARD,
    ITEM_TITLE,
    ITEM_SEAT,
    ITEM_PRICE,
}

@Composable
fun TicketsScreen(state: TheaterState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp).testTag(TicketsTags.SCREEN)) {
        Text(text = "My tickets", style = MaterialTheme.typography.headlineMedium)

        if (state.tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No tickets yet - check the playbill 🎭",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(TicketsTags.EMPTY),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(TicketsTags.LIST)
                    .testListLength(state.tickets.size),
            ) {
                itemsIndexed(state.tickets) { index, ticket ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TicketsTags.CARD, index)
                            .testListItem(index),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = ticket.performanceTitle,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag(TicketsTags.ITEM_TITLE),
                            )
                            Text(
                                text = "Row ${ticket.row}, seat ${ticket.number}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp).testTag(TicketsTags.ITEM_SEAT),
                            )
                            Text(
                                text = "¥${ticket.price}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 4.dp).testTag(TicketsTags.ITEM_PRICE),
                            )
                        }
                    }
                }
            }
        }
    }
}
