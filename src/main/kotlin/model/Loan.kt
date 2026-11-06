package org.example.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Loan(
    val id: Int = 0,
    val book: Book,
    val borrower: Borrower,
    val borrowedAt: LocalDate,
    val dueDate: LocalDate,
    val returnedAt: LocalDate? = null,
    val version: Int = 0
) {
    val isReturned get() = returnedAt != null
    val isOverdue  get() = !isReturned && LocalDate.now().isAfter(dueDate)
    val daysOverdue: Int get() =
        if (isReturned) 0
        // Saturate: daysToReturn = Int.MIN_VALUE yields a 2^31-day gap that would wrap negative.
        else ChronoUnit.DAYS.between(dueDate, LocalDate.now()).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
}
