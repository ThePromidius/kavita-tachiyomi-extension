package eu.kanade.tachiyomi.extension.all.kavita

import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import java.util.TimeZone
object MDConstants {

    val uuidRegex =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    const val mangaLimit = 20
    const val latestChapterLimit = 100

    const val manga = "manga"
    const val coverArt = "cover_art"
    const val scanlator = "scanlation_group"
    const val author = "author"
    const val artist = "artist"

    const val cdnUrl = "https://uploads.mangadex.org"
    const val apiUrl = "https://api.mangadex.org"
    const val apiMangaUrl = "$apiUrl/manga"
    const val apiChapterUrl = "$apiUrl/chapter"
    const val atHomePostUrl = "https://api.mangadex.network/report"
    val whitespaceRegex = "\\s".toRegex()

    const val mdAtHomeTokenLifespan = 5 * 60 * 1000

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    // "2021-12-07T03:02:31.609Z"

    const val prefixIdSearch = "id:"
    const val prefixChSearch = "ch:"
}
