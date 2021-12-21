package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class metadataGenres(
    val id: Int,
    val title: String,
)
@Serializable
data class metadataPeople(
    val id: Int,
    val name: String,
    val role: Int
)
@Serializable
data class metadataTags(
    val id: Int,
    val name: String,
    val role: Int
)
@Serializable
data class metadataAgeRatings(
    val value: Int,
    val title: String
)
@Serializable
data class metadataLanguages(
    val isoCode: String,
    val title: String
)
data class metadataPayload(
    var readStatus: ArrayList<String> = arrayListOf< String>(),

    var genres: ArrayList<Int> = arrayListOf<Int>(),

    var people: ArrayList<Int> = arrayListOf<Int>(),

    var tags: ArrayList<Int> = arrayListOf<Int>(),

    var ageRating: ArrayList<Int> = arrayListOf<Int>(),

    var language: ArrayList<String> = arrayListOf<String>(),
)
