package kabuki.sample.model

enum class Genre(val displayName: String) {
    CLASSIC("Classic"),
    MODERN("Modern"),
    KIDS("Kids"),
}

data class Performance(
    val id: String,
    val title: String,
    val genre: Genre,
    val shortDescription: String,
    val fullDescription: String,
    val actors: List<String>,
    val durationMinutes: Int,
    val price: Int,
)

data class Seat(
    val row: Int,
    val number: Int,
    val taken: Boolean,
)

data class Ticket(
    val performanceTitle: String,
    val row: Int,
    val number: Int,
    val price: Int,
)

data class Review(
    val author: String,
    val rating: Int,
    val text: String,
)
