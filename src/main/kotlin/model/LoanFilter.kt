package org.example.model

data class LoanFilter(
    val borrowerName: String?,      // partial name search for humans, never identity matching
    val bookId: Int?,
    val onlyActive: Boolean,
    val page: Int? = null,
    val size: Int? = null,
    val userId: Int? = null         // exact account filter
)
