package org.example.http

data class LoanRequest(
    val bookId: Int,
    val userId: Int? = null,    // omitted: the authenticated actor borrows for themself
    val daysToReturn: Int
)
