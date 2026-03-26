package org.example.model

data class Book(
    val id: Int = 0,
    val title: String,
    val author: String,
    val category: BookCategory,
    val quantity: Int
)
