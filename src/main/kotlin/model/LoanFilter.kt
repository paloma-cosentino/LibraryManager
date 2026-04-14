package org.example.model

data class LoanFilter(
    val borrowerName: String? = null,
    val bookId: Int? = null,
    val onlyActive: Boolean = false,   // apenas empréstimos não devolvidos
    val page: Int? = null,
    val size: Int? = null
)
