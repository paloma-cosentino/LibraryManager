package org.example.service

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookExceptions
import org.example.model.LoanExceptions
import org.example.model.LoanFilter
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import kotlin.test.*

class LoanServiceTest {

    private lateinit var service: LoanService
    private lateinit var bookRepository: InMemoryBookRepository
    private lateinit var dune: Book
    private lateinit var foundation: Book

    @BeforeTest
    fun setUp() {
        val herbert = Author(1, "Frank Herbert")
        val asimov  = Author(2, "Isaac Asimov")

        bookRepository = InMemoryBookRepository()
        dune           = bookRepository.save(Book(0, "Dune",       herbert, BookCategory.SCIENCE_FICTION, 2))
        foundation     = bookRepository.save(Book(0, "Foundation", asimov,  BookCategory.SCIENCE_FICTION, 1))

        service = LoanService(bookRepository, InMemoryLoanRepository())
    }

    // ─── getAllLoans ──────────────────────────────────────────────────────────

    @Test
    fun `getAllLoans returns empty list when no loans exist`() {
        assertTrue(service.getAllLoans(LoanFilter(borrowerName = null, bookId = null, onlyActive = false)).data.isEmpty())
    }

    @Test
    fun `getAllLoans returns all active loans`() {
        service.borrowBook(dune.id, "Alice", 14)
        service.borrowBook(foundation.id, "Bob", 7)

        assertEquals(2, service.getAllLoans(LoanFilter(borrowerName = null, bookId = null, onlyActive = false)).data.size)
    }

    @Test
    fun `getAllLoans filters by borrower name`() {
        service.borrowBook(dune.id,       "Alice", 14)
        service.borrowBook(foundation.id, "Bob",   7)

        val result = service.getAllLoans(LoanFilter(borrowerName = "Alice", bookId = null, onlyActive = false)).data

        assertEquals(1, result.size)
        assertEquals("Alice", result.first().borrowerName)
    }

    @Test
    fun `getAllLoans with onlyActive=true excludes returned loans`() {
        val loan = service.borrowBook(dune.id, "Alice", 14)
        service.borrowBook(foundation.id, "Bob", 7)
        service.returnBook(loan.id)

        val active = service.getAllLoans(LoanFilter(borrowerName = null, bookId = null, onlyActive = true)).data

        assertEquals(1, active.size)
        assertEquals("Bob", active.first().borrowerName)
    }

    // ─── getLoanById ─────────────────────────────────────────────────────────

    @Test
    fun `getLoanById returns loan when it exists`() {
        val loan  = service.borrowBook(dune.id, "Alice", 14)
        val found = service.getLoanById(loan.id)

        assertEquals(loan.id, found.id)
        assertEquals("Alice", found.borrowerName)
    }

    @Test
    fun `getLoanById throws LoanNotFoundException when loan does not exist`() {
        assertFailsWith<LoanExceptions.LoanNotFoundException> {
            service.getLoanById(999)
        }
    }

    // ─── borrowBook ──────────────────────────────────────────────────────────

    @Test
    fun `borrowBook returns loan with generated id`() {
        val loan = service.borrowBook(dune.id, "Alice", 14)

        assertTrue(loan.id > 0)
    }

    @Test
    fun `borrowBook persists correct data`() {
        val loan = service.borrowBook(dune.id, "Alice", 14)

        assertEquals("Alice",   loan.borrowerName)
        assertEquals(dune.id,   loan.book.id)
        assertNull(loan.returnedAt)
    }

    @Test
    fun `borrowBook with negative daysToReturn creates an overdue loan`() {
        val loan = service.borrowBook(dune.id, "Alice", -3)

        assertEquals(loan.borrowedAt.minusDays(3), loan.dueDate)
        assertTrue(loan.isOverdue)
    }

    @Test
    fun `borrowBook decrements book quantity`() {
        service.borrowBook(dune.id, "Alice", 14)

        val remaining = bookRepository.findById(dune.id)!!.quantity
        assertEquals(1, remaining)
    }

    @Test
    fun `borrowBook sets dueDate correctly`() {
        val loan = service.borrowBook(dune.id, "Alice", 7)

        assertEquals(loan.borrowedAt.plusDays(7), loan.dueDate)
    }

    @Test
    fun `borrowBook throws BookNotFoundException when book does not exist`() {
        assertFailsWith<BookExceptions.BookNotFoundException> {
            service.borrowBook(999, "Alice", 14)
        }
    }

    @Test
    fun `borrowBook throws BookNotAvailableException when quantity is zero`() {
        // foundation has quantity=1
        service.borrowBook(foundation.id, "Alice", 14)

        assertFailsWith<LoanExceptions.BookNotAvailableException> {
            service.borrowBook(foundation.id, "Bob", 14)
        }
    }

    // ─── returnBook ──────────────────────────────────────────────────────────

    @Test
    fun `returnBook sets returnedAt`() {
        val loan     = service.borrowBook(dune.id, "Alice", 14)
        val returned = service.returnBook(loan.id)

        assertNotNull(returned.returnedAt)
    }

    @Test
    fun `returnBook increments book quantity`() {
        val loan = service.borrowBook(foundation.id, "Alice", 14)
        assertEquals(0, bookRepository.findById(foundation.id)!!.quantity)

        service.returnBook(loan.id)

        assertEquals(1, bookRepository.findById(foundation.id)!!.quantity)
    }

    @Test
    fun `returnBook throws LoanNotFoundException when loan does not exist`() {
        assertFailsWith<LoanExceptions.LoanNotFoundException> {
            service.returnBook(999)
        }
    }

    @Test
    fun `returnBook throws LoanAlreadyReturnedException when loan already returned`() {
        val loan = service.borrowBook(dune.id, "Alice", 14)
        service.returnBook(loan.id)

        assertFailsWith<LoanExceptions.LoanAlreadyReturnedException> {
            service.returnBook(loan.id)
        }
    }
}
