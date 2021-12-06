package eu.kanade.tachiyomi.extension.all.kavita

import java.text.SimpleDateFormat
import java.util.*

object MDConstants {
    const val manga = "manga"
    const val coverArt = "cover_art"
    const val scanlator = "scanlation_group"
    const val author = "author"
    const val artist = "artist"
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
}
