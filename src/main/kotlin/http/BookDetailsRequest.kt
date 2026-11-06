package org.example.http

/**
 * PUT body: book details only. [quantity] is declared solely so a client that still sends it can be
 * rejected (Jackson would otherwise silently ignore the unknown field); stock changes go through PATCH.
 */
data class BookDetailsRequest(
    val title: String,
    val author: String,
    val category: String,
    val publishedYear: Int? = null,
    val edition: String? = null,
    val quantity: Int? = null,
)
