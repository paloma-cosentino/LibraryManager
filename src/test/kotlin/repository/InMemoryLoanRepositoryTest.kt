package org.example.repository

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Borrower
import org.example.model.Loan
import org.example.model.LoanFilter
import java.time.LocalDate
import kotlin.test.*

class InMemoryLoanRepositoryTest {
    private val repository = InMemoryLoanRepository()
    private val book = Book(1, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, 3)
    private val today = LocalDate.now()

    @Test
    fun `update increments version and stale update throws ConcurrentUpdateException`() {
        val saved = repository.save(Loan(book = book, borrower = Borrower(1, "Alice", "alice@example.com"), borrowedAt = today, dueDate = today.plusDays(14)))

        val updated = repository.update(saved.copy(returnedAt = today))

        assertEquals(1, updated.version)
        assertEquals(1, repository.findById(saved.id)?.version)
        assertFailsWith<ConcurrentUpdateException> { repository.update(saved.copy(returnedAt = today.plusDays(1))) }
        assertEquals(today, repository.findById(saved.id)?.returnedAt)
    }

    @Test
    fun `findAll filters by exact userId and by partial borrower name`() {
        repository.save(Loan(book = book, borrower = Borrower(1, "Al", "al@example.com"), borrowedAt = today, dueDate = today))
        repository.save(Loan(book = book, borrower = Borrower(2, "Alice", "alice@example.com"), borrowedAt = today, dueDate = today))

        assertEquals(listOf(1), repository.findAll(LoanFilter(null, null, false, userId = 1)).data.map { it.borrower.id })
        assertEquals(listOf(2), repository.findAll(LoanFilter("lic", null, false)).data.map { it.borrower.id })
    }

    @Test
    fun `findActiveByUserId returns only that account's unreturned loans`() {
        val alice = Borrower(1, "Alice", "alice@example.com")
        val active = repository.save(Loan(book = book, borrower = alice, borrowedAt = today, dueDate = today))
        repository.save(Loan(book = book, borrower = alice, borrowedAt = today, dueDate = today, returnedAt = today))
        repository.save(Loan(book = book, borrower = Borrower(2, "Bob", "bob@example.com"), borrowedAt = today, dueDate = today))

        assertEquals(listOf(active), repository.findActiveByUserId(1))
    }
}
