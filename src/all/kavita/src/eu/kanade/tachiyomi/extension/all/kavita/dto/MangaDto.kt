package eu.kanade.tachiyomi.extension.all.kavita.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class UserParams(
    val pageNumber: Int,
    val pageSize: Int = 50
)

@Serializable
data class FilterDto(
    val mangaFormat: MangaFormat,
    val pageSize: Int = 50
)

@Serializable
enum class MangaFormat(val format: Int) {
    Image(0),
    Archive(1),
    Unknown(2),
    Epub(3),
    Pdf(4)
}

@Serializable
data class LibraryDto(
    val id: String,
    val name: String,
    val type: Int
)

// @Serializable
// data class SeriesDto(
//    val id: Int,
//    @SerialName("name") val title: String,
//    val thumbnail_url: String? = "",
//    @SerialName("summary") val description: String? = ""
// )

@Serializable
data class SeriesDto(
    val id: Int,
    val name: String,
    val originalName: String = "",
    val thumbnail_url: String? = "",
    val localizedName: String? = "",
    val sortName: String? = "",
    val summary: String? = "",
    val pages: Int,
    val coverImageLocked: Boolean = true,
    val pagesRead: Int,
    val userRating: Int,
    val userReview: String? = "",
    val format: Int,
    val created: String? = "",
    val libraryId: Int,
    val libraryName: String? = ""
)

@Serializable
data class VolumeDto(
    val id: Int,
    val number: Int,
    val name: String,
    val pages: Int,
    val pagesRead: Int,
    val lastModified: String
)

abstract class KavitaManga : SManga {
    abstract val id: Int
}
