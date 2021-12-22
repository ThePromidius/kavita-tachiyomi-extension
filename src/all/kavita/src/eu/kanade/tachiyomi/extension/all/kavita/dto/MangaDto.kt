package eu.kanade.tachiyomi.extension.all.kavita.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

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

@Serializable
data class SeriesDto(
    val id: Int,
    val name: String,
    val originalName: String = "",
    val thumbnail_url: String? = "",
    val localizedName: String? = "",
    val sortName: String? = "",

    // deprecated: val summary: String? = "",
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
data class SeriesMetadataDto(
    val id: Int,
    val summary: String,
    val writers: List<Person>,
    val artists: List<Person>,
    val genres: List<Genres>,
    val seriesId: Int,
    val ageRating: Int

)
@Serializable
data class Genres(
    val title: String
)
@Serializable
data class Person(
    val name: String
)

@Serializable
data class VolumeDto(
    val id: Int,
    val number: Int,
    val name: String,
    val pages: Int,
    val pagesRead: Int,
    val lastModified: String,
    val created: String,
    val seriesId: Int,
    val chapters: List<ChapterDto> = emptyList()
)

@Serializable
data class ChapterDto(
    val id: Int,
    val range: String,
    val number: String,
    val pages: Int,
    val isSpecial: Boolean,
    val title: String,
    val pagesRead: Int,
    val coverImageLocked: Boolean,
    val volumeId: Int,
    val created: String
)

abstract class KavitaManga : SManga {
    abstract val id: Int
}

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
