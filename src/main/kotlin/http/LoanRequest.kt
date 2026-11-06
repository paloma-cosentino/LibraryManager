package org.example.http

data class LoanRequest(
    val bookId: Int,
    val userId: Int,
    val daysToReturn: Int
)
