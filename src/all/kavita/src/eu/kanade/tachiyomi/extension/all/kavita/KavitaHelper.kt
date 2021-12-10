package eu.kanade.tachiyomi.extension.all.kavita

import eu.kanade.tachiyomi.extension.all.kavita.dto.PaginationInfo
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesDto
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*

class KavitaHelper {
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    } /*
    fun parseDate(dateAsString: String): Long =
        MDConstants.dateFormatter.parse(dateAsString)?.time ?: 0
*/
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    fun parseDate(dateAsString: String): Long =
        dateFormatter.parse(dateAsString)?.time ?: 0

    fun convertPagination(page: Int): Int {
        var pageNum = page - 1
        if (pageNum < 0) pageNum = 0
        return pageNum
    }

    fun hasNextPage(response: Response): Boolean {
        var paginationHeader = response.header("Pagination")
        var hasNextPage = false
        if (!paginationHeader.isNullOrEmpty()) {
            var paginationInfo = json.decodeFromString<PaginationInfo>(paginationHeader)
            hasNextPage = paginationInfo.currentPage < paginationInfo.totalPages
        }
        return hasNextPage
    }

    fun getIdFromUrl(url: String): Int {
        return url.split("/").last().toInt()
    }

    fun createSeriesDto(obj: SeriesDto, baseUrl: String): SManga =
        SManga.create().apply {
//            println("createSeriesDto")
//            println(obj)
            url = "$baseUrl/Series/${obj.id}"
            title = obj.name
            // artist = obj.artist
            // author = obj.author
            description = obj.summary
            // genre = obj.genres.joinToString(", ")
            // status = obj.status
            thumbnail_url = "$baseUrl/image/series-cover?seriesId=${obj.id}"
        }
}
