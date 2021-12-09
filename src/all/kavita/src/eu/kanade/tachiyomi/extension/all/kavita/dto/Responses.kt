package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationDto(
    val username: String,
    val token: String,
    val apiKey: String
)

@Serializable
data class PaginationInfo(
    val currentPage: Int,
    val itemsPerPage: Int,
    val totalItems: Int,
    val totalPages: Int
)
