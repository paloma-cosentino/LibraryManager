package org.example.http

data class LoanRequest(
    val bookId: Int,
    val borrowerName: String,
    val daysToReturn: Int
)
