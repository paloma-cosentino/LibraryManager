package org.example.model

import java.time.LocalDate

data class Loan(
    val id: Int = 0,
    val book: Book,
    val borrowerName: String,       // stub — vira FK de User no futuro
    val borrowedAt: LocalDate,
    val dueDate: LocalDate,
    val returnedAt: LocalDate? = null,
    val version: Int = 0
) {
    val isReturned get() = returnedAt != null
    val isOverdue  get() = !isReturned && LocalDate.now().isAfter(dueDate)
}
