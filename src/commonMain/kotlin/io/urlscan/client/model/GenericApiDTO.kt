package io.urlscan.client.model

import kotlinx.serialization.Serializable

@Serializable
data class QuotasResponse(
    val limits: Limits,
    val used: Used
)

@Serializable
data class Limits(
    val public: Int,
    val private: Int,
    val unlisted: Int,
    val retrieve: Int,
    val search: Int
)

@Serializable
data class Used(
    val public: Int,
    val private: Int,
    val unlisted: Int,
    val retrieve: Int,
    val search: Int
)

@Serializable
data class ProUsernameResponse(
    val username: String
)
