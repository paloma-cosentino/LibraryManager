package org.example.http

data class CreateBookRequest(
    val title: String,
    val author: String,
    val category: String,
    val quantity: Int
)
