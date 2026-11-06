package org.example.http

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.repository.AlwaysConflictingBookRepository
import org.example.repository.AlwaysConflictingLoanRepository
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import org.example.service.BookService
import org.example.service.LoanService
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CONFLICT
import kotlin.test.*

class ConcurrentUpdateHandlingTest {

    private val storedBooks = InMemoryBookRepository()
    private val book = storedBooks.save(Book(0, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, 2))
    private val conflictingBooks = AlwaysConflictingBookRepository(storedBooks)
    private val bookApp = bookRoutes(BookHandler(BookService(conflictingBooks, InMemoryAuthorRepository())))

    @Test
    fun `PATCH book quantity returns 409 when conflicts exhaust retries`() {
        val response = bookApp(
            Request(PATCH, "/books/${book.id}/quantity")
                .header("Content-Type", "application/json")
                .body("""{"change":1}""")
        )

        assertEquals(CONFLICT, response.status)
        assertTrue(response.bodyString().contains("modified concurrently, try again"))
    }

    @Test
    fun `PUT book returns 409 when conflicts exhaust retries`() {
        val response = bookApp(
            Request(PUT, "/books/${book.id}")
                .header("Content-Type", "application/json")
                .body("""{"title":"Dune Messiah","author":"Frank Herbert","category":"Science Fiction"}""")
        )

        assertEquals(CONFLICT, response.status)
    }

    @Test
    fun `POST loans returns 409 when conflicts exhaust retries`() {
        val loanApp = loanRoutes(LoanHandler(LoanService(conflictingBooks, InMemoryLoanRepository())))

        val response = loanApp(
            Request(POST, "/loans")
                .header("Content-Type", "application/json")
                .body("""{"bookId":${book.id},"borrowerName":"Alice","daysToReturn":14}""")
        )

        assertEquals(CONFLICT, response.status)
        assertTrue(response.bodyString().contains("modified concurrently, try again"))
    }

    @Test
    fun `PATCH loan return returns 409 when conflicts exhaust retries`() {
        val storedLoans = InMemoryLoanRepository()
        val loan = LoanService(storedBooks, storedLoans).borrowBook(book.id, "Alice", 14)
        val loanApp = loanRoutes(LoanHandler(LoanService(storedBooks, AlwaysConflictingLoanRepository(storedLoans))))

        val response = loanApp(Request(PATCH, "/loans/${loan.id}/return"))

        assertEquals(CONFLICT, response.status)
        assertFalse(storedLoans.findById(loan.id)!!.isReturned)
        assertEquals(1, storedBooks.findById(book.id)?.quantity)
    }
}
