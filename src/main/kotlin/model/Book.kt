package org.example.model

data class Book(
    val id: Int = 0,
    val title: String,
    val author: Author,
    val category: BookCategory,
    val quantity: Int,
    val publishedYear: Int? = null,
    val edition: String? = null,
)
