package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class AggregateVolume(
    val id: Int,
    val name: String,
    val pages: Int,
    val pagesRead: Int,
    val lastModified: String,
    val created: String,
    val seriesId: Int,
    val chapters: List<AggregateChapter> = emptyList()
)
@Serializable
data class AggregateChapter(
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
