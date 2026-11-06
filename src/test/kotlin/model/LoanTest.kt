package org.example.model

import java.time.LocalDate
import kotlin.test.*

class LoanTest {
    private val today = LocalDate.now()
    private val book = Book(1, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, 1)

    private fun loanDue(dueDate: LocalDate, returnedAt: LocalDate? = null) =
        Loan(1, book, Borrower(1, "Alice", "alice@example.com"), today.minusDays(30), dueDate, returnedAt)

    @Test
    fun `daysOverdue counts days past the due date`() {
        assertEquals(3, loanDue(today.minusDays(3)).daysOverdue)
        assertEquals(1, loanDue(today.minusDays(1)).daysOverdue)
    }

    @Test
    fun `daysOverdue is zero when not yet due or due today`() {
        assertEquals(0, loanDue(today.plusDays(2)).daysOverdue)
        assertEquals(0, loanDue(today).daysOverdue)
    }

    @Test
    fun `daysOverdue saturates instead of wrapping when the gap exceeds Int range`() {
        assertEquals(Int.MAX_VALUE, loanDue(today.plusDays(Int.MIN_VALUE.toLong())).daysOverdue)
    }

    @Test
    fun `daysOverdue is zero once returned even if returned late`() {
        assertEquals(0, loanDue(today.minusDays(5), returnedAt = today).daysOverdue)
    }
}
