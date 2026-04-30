package org.example.model

data class BookFilter(
    val title: String?,
    val author: String?,
    val category: String?,
    val page: Int? = null,
    val size: Int? = null
)
