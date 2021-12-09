package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KavitaComicsDto(
    val id: Int,
    @SerialName("name",) val title: String,
    val thumbnail_url: String? = "",
    @SerialName("summary") val description: String = ""
)
@Serializable
data class KavitaComicsSearch(
    val seriesId: Int,
    val name: String,
    val originalName: String,
    val sortName: String,
    val localizedName: String,
    val format: Int,
    val libraryName: String,
    val libraryId: Int
)
@Serializable
data class KavitaComicsDetailsDto(
    val id: Int,
    val name: String,
    val originalName: String = "",
    val thumbnail_url: String? = "",
    val localizedName: String? = "",
    val sortName: String? = "",
    val summary: String = "This is summary",
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
data class Login(
    val username: String,
    val token: String,
    val apiKey: String
)
