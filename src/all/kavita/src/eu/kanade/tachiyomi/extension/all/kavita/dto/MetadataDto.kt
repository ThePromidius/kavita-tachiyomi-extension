package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable
/**
* This file contains all class for filtering
*  */
@Serializable
data class MetadataGenres(
    val id: Int,
    val title: String,
)
@Serializable
data class MetadataPeople(
    val id: Int,
    val name: String,
    val role: Int
)
@Serializable
data class MetadataTags(
    val id: Int,
    val name: String,
    val role: Int
)
@Serializable
data class MetadataAgeRatings(
    val value: Int,
    val title: String
)
@Serializable
data class MetadataLanguages(
    val isoCode: String,
    val title: String
)
@Serializable
data class MetadataLibrary(
    val id: Int,
    val name: String,
    val type: Int
)

data class MetadataPayload(
    var readStatus: ArrayList<String> = arrayListOf< String>(),
    var genres: ArrayList<Int> = arrayListOf<Int>(),
    var tags: ArrayList<Int> = arrayListOf<Int>(),
    var ageRating: ArrayList<Int> = arrayListOf<Int>(),
    var formats: ArrayList<String> = arrayListOf<String>(),
    var people: ArrayList<Int> = arrayListOf<Int>(),
    var language: ArrayList<String> = arrayListOf<String>(),
    var libraries: ArrayList<Int> = arrayListOf<Int>()
)
