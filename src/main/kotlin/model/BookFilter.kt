package org.example.model

data class BookFilter(
    val title: String?= null,
    val author: String?= null,
    val category: String?= null,
    val page: Int? = null,
    val size: Int? = null
)
