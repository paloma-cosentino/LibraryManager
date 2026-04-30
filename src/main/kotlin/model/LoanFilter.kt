package org.example.model

data class LoanFilter(
    val borrowerName: String?,
    val bookId: Int?,
    val onlyActive: Boolean,
    val page: Int? = null,
    val size: Int? = null
)
