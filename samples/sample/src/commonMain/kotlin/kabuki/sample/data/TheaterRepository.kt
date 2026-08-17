package kabuki.sample.data

import kabuki.sample.model.Genre
import kabuki.sample.model.Performance
import kabuki.sample.model.Review
import kabuki.sample.model.Seat
import kabuki.sample.model.Ticket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fake repository with network-like delays - the source of asynchrony for
 * shimmers, loaders and Kabuki retry tests.
 *
 * Delays run on Dispatchers.Default (real time), like actual network calls.
 * On the composition's test dispatcher a delay would be consumed by the virtual
 * clock during waitForIdle, making loading states unobservable in tests.
 */
class TheaterRepository {

    /**
     * Test hook for the playbill loading state.
     *
     * A test that wants to assert the shimmer must not race a 1.5 s timer: on a
     * slow device the data can arrive before the assertion runs, and the test
     * fails for no good reason. With the gate armed, loading waits for
     * [releasePlaybill] instead of the fake network delay, so the test decides
     * when data arrives.
     */
    private var playbillGate: CompletableDeferred<Unit>? = null

    /** Freezes playbill loading until [releasePlaybill]. Call before setContent. */
    fun holdPlaybill() {
        playbillGate = CompletableDeferred()
    }

    /** Lets the frozen playbill loading finish. */
    fun releasePlaybill() {
        playbillGate?.complete(Unit)
    }

    suspend fun loadPerformances(): List<Performance> {
        val gate = playbillGate
        if (gate != null) {
            gate.await()
            return performances
        }
        return networkCall(1_500) {
            performances
        }
    }

    suspend fun loadDetails(id: String): Performance {
        return networkCall(800) {
            performances.first { it.id == id }
        }
    }

    suspend fun loadSeats(performanceId: String): List<Seat> {
        return networkCall(600) {
            (1..ROWS).flatMap { row ->
                (1..SEATS_PER_ROW).map { number ->
                    Seat(row = row, number = number, taken = (row * 31 + number * 7) % 4 == 0)
                }
            }
        }
    }

    suspend fun loadReviews(performanceId: String): List<Review> {
        return networkCall(500) {
            val authors = listOf(
                "Aiko", "Haruto", "Mei", "Sota", "Yui", "Ren",
                "Hana", "Kaito", "Sakura", "Riku",
            )
            val phrases = listOf(
                "A stunning performance, the staging is impeccable.",
                "The costumes alone are worth the ticket price.",
                "Slow in the second act, but the finale makes up for it.",
                "The lead actor's mie poses gave me chills.",
                "Great for a first-time kabuki visitor.",
                "The live shamisen music carries the whole show.",
            )
            List(REVIEWS_COUNT) { index ->
                Review(
                    author = "${authors[index % authors.size]} ${index + 1}",
                    rating = index % 5 + 1,
                    text = phrases[index % phrases.size],
                )
            }
        }
    }

    suspend fun buyTicket(performance: Performance, seat: Seat): Ticket {
        return networkCall(700) {
            Ticket(
                performanceTitle = performance.title,
                row = seat.row,
                number = seat.number,
                price = performance.price,
            )
        }
    }

    private suspend fun <T> networkCall(delayMillis: Long, block: () -> T): T {
        return withContext(Dispatchers.Default) {
            delay(delayMillis.milliseconds)
            block()
        }
    }

    companion object {
        const val ROWS = 10
        const val SEATS_PER_ROW = 8
        const val REVIEWS_COUNT = 30
    }
}

private val performances = listOf(
    Performance(
        id = "chushingura",
        title = "Chushingura: The Treasury of Loyal Retainers",
        genre = Genre.CLASSIC,
        shortDescription = "Forty-seven ronin avenge their fallen lord.",
        fullDescription = "The classic play about forty-seven ronin who avenge the death " +
            "of their master. Eleven acts of loyalty, honor and sacrifice - the " +
            "centerpiece of the kabuki repertoire since 1748.",
        actors = listOf("Ichikawa Danjuro", "Onoe Kikugoro", "Nakamura Kanzaburo"),
        durationMinutes = 240,
        price = 3_500,
    ),
    Performance(
        id = "yotsuya",
        title = "Yotsuya Kaidan",
        genre = Genre.CLASSIC,
        shortDescription = "The ghost of Oiwa - the most famous kaidan play.",
        fullDescription = "Betrayal, poison and revenge from beyond. The ghost of the " +
            "disfigured Oiwa haunts her treacherous husband. Famous stage tricks and " +
            "instant costume changes are the signature of this production.",
        actors = listOf("Bando Tamasaburo", "Ichikawa Ebizo"),
        durationMinutes = 180,
        price = 2_800,
    ),
    Performance(
        id = "sukeroku",
        title = "Sukeroku: Flower of Edo",
        genre = Genre.CLASSIC,
        shortDescription = "A dandy of Edo searches for a stolen sword.",
        fullDescription = "The benchmark of the aragoto style: the dashing Sukeroku picks " +
            "fights across the Yoshiwara pleasure quarter while hunting for his " +
            "family's stolen sword. The hero's purple headband is one of the most " +
            "recognizable images in kabuki.",
        actors = listOf("Ichikawa Danjuro", "Nakamura Shikan"),
        durationMinutes = 150,
        price = 3_000,
    ),
    Performance(
        id = "neon",
        title = "Neon Samurai",
        genre = Genre.MODERN,
        shortDescription = "An experimental staging of a classic duel story.",
        fullDescription = "An experimental production: traditional mie poses under synth " +
            "lighting, kumadori makeup in neon colors, and a classic duel story told " +
            "with modern stagecraft.",
        actors = listOf("Matsumoto Koshiro", "Ichikawa Somegoro"),
        durationMinutes = 120,
        price = 4_200,
    ),
    Performance(
        id = "autumn",
        title = "Autumn Rain",
        genre = Genre.MODERN,
        shortDescription = "A quiet one-act drama about a chance encounter.",
        fullDescription = "Two strangers wait out the rain under the same roof. A modern " +
            "one-act play about small talk, long silences and the moment a stranger " +
            "stops being one.",
        actors = listOf("Onoe Kikunosuke", "Nakamura Shichinosuke"),
        durationMinutes = 90,
        price = 2_200,
    ),
    Performance(
        id = "momotaro",
        title = "Momotaro the Peach Boy",
        genre = Genre.KIDS,
        shortDescription = "The tale of the peach-born hero - for the whole family.",
        fullDescription = "A boy born from a giant peach sets out for the island of demons " +
            "with his friends - a dog, a monkey and a pheasant. Bright costumes, songs " +
            "and nothing scary.",
        actors = listOf("Nakamura Kantaro", "the theater's youth troupe"),
        durationMinutes = 75,
        price = 1_200,
    ),
)
