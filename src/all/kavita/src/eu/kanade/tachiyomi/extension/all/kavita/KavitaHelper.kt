package eu.kanade.tachiyomi.extension.all.kavita

import eu.kanade.tachiyomi.extension.all.kavita.dto.PaginationInfo
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesDto
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class KavitaHelper {
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    }

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    fun parseDate(dateAsString: String): Long =
        dateFormatter.parse(dateAsString)?.time ?: 0

    fun hasNextPage(response: Response): Boolean {
        var paginationHeader = response.header("Pagination")
        var hasNextPage = false
        if (!paginationHeader.isNullOrEmpty()) {
            var paginationInfo = json.decodeFromString<PaginationInfo>(paginationHeader)
            hasNextPage = paginationInfo.currentPage + 1 > paginationInfo.totalPages
        }
        return !hasNextPage
    }

    fun getIdFromUrl(url: String): Int {
        return url.split("/").last().toInt()
    }

    fun createSeriesDto(obj: SeriesDto, baseUrl: String): SManga =
        SManga.create().apply {
            url = "$baseUrl/Series/${obj.id}"
            title = obj.name
            //TODO(Should delete this when sure they are not used)

            // Not used: (accessed from MangaDto):  artist = obj.artist
            // Not used: (accessed from MangaDto):  author = obj.author
            // Deprecated (accessed from MangaDto): description = obj.summary
            // Not used: (accessed from MangaDto):  genre = obj.genres.joinToString(", ")
            // status = obj.status
            thumbnail_url = "$baseUrl/image/series-cover?seriesId=${obj.id}"
        }
}
