package org.example.service

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.LoanExceptions
import org.example.model.LoanFilter
import org.example.repository.HookedBookRepository
import org.example.repository.HookedLoanRepository
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import kotlin.test.*

class LoanServiceConcurrencyTest {

    private val bookRepository = HookedBookRepository(InMemoryBookRepository())
    private val loanRepository = HookedLoanRepository(InMemoryLoanRepository())
    private val service = LoanService(bookRepository, loanRepository)

    private fun saveBook(quantity: Int): Book =
        bookRepository.save(Book(0, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, quantity))

    private fun allLoans() =
        service.getAllLoans(LoanFilter(borrowerName = null, bookId = null, onlyActive = false)).data

    @Test
    fun `a competing borrow that takes the last copy between our read and our write leaves exactly one loan`() {
        val book = saveBook(quantity = 1)
        bookRepository.beforeNextWrite { service.borrowBook(book.id, "Bob", 14) }

        assertFailsWith<LoanExceptions.BookNotAvailableException> {
            service.borrowBook(book.id, "Alice", 14)
        }

        assertEquals(listOf("Bob"), allLoans().map { it.borrowerName })
        assertEquals(0, bookRepository.findById(book.id)?.quantity)
    }

    @Test
    fun `a borrow that happens during a return is not lost`() {
        val book = saveBook(quantity = 2)
        val alicesLoan = service.borrowBook(book.id, "Alice", 14)
        bookRepository.beforeNextWrite { service.borrowBook(book.id, "Bob", 14) }

        service.returnBook(alicesLoan.id)

        assertEquals(1, bookRepository.findById(book.id)?.quantity)
        assertEquals(mapOf("Alice" to true, "Bob" to false), allLoans().associate { it.borrowerName to it.isReturned })
    }

    @Test
    fun `a loan returned by another process between our read and our write is not returned twice`() {
        val book = saveBook(quantity = 1)
        val loan = service.borrowBook(book.id, "Alice", 14)
        loanRepository.beforeNextWrite { service.returnBook(loan.id) }

        assertFailsWith<LoanExceptions.LoanAlreadyReturnedException> {
            service.returnBook(loan.id)
        }

        assertEquals(1, bookRepository.findById(book.id)?.quantity)
        assertTrue(service.getLoanById(loan.id).isReturned)
    }
}
