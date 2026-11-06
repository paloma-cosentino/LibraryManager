package org.example.repository

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Borrower
import org.example.model.Loan
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
}
