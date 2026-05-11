package org.example.http

data class BookRequest(
    val title: String,
    val author: String,
    val category: String,
    val quantity: Int,
    val publishedYear: Int? = null,
    val edition: String? = null,
)
