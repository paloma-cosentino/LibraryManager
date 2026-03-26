package org.example.http

data class UpdateBookRequest(
    val title: String,
    val author: String,
    val category: String,
    val quantity: Int
)

