package org.example.service

import org.example.model.Actor
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.LoanExceptions
import org.example.model.LoanFilter
import org.example.model.Role
import org.example.model.User
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import org.example.repository.InMemoryUserRepository
import kotlin.test.*

class LoanServiceOverdueBlockTest {

    private val books = InMemoryBookRepository()
    private val loans = InMemoryLoanRepository()
    private val users = InMemoryUserRepository()
    private val service = LoanService(books, loans, users)

    private val dune = books.save(Book(0, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, 5))
    private val alice = users.save(User(name = "Alice", email = "alice@example.com", passwordHash = "h"))
    private val bob = users.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))
    private val asAlice = Actor(alice.id, Role.USER)
    private val admin = Actor(1001, Role.ADMIN)

    private fun stock() = books.findById(dune.id)!!.quantity
    private fun loanCount() = loans.findAll(LoanFilter(null, null, false)).total

    @Test
    fun `an overdue active loan blocks borrowing, leaving stock and loans untouched`() {
        service.borrowBook(asAlice, dune.id, null, -1)
        val stockBefore = stock()

        assertFailsWith<LoanExceptions.BorrowerHasOverdueLoansException> { service.borrowBook(asAlice, dune.id, null, 14) }

        assertEquals(stockBefore, stock())
        assertEquals(1, loanCount())
    }

    @Test
    fun `active loans that are not yet overdue do not block`() {
        service.borrowBook(asAlice, dune.id, null, 14)
        service.borrowBook(asAlice, dune.id, null, 0)

        assertEquals(3, service.borrowBook(asAlice, dune.id, null, 7).let { loanCount() })
    }

    @Test
    fun `an overdue loan that has been returned does not block`() {
        val late = service.borrowBook(asAlice, dune.id, null, -3)
        service.returnBook(late.id)

        service.borrowBook(asAlice, dune.id, null, 14)

        assertEquals(2, loanCount())
    }

    @Test
    fun `a negative daysToReturn loan blocks the next borrow`() {
        service.borrowBook(asAlice, dune.id, null, -3)

        assertFailsWith<LoanExceptions.BorrowerHasOverdueLoansException> { service.borrowBook(asAlice, dune.id, null, -3) }
    }

    @Test
    fun `an admin borrowing on behalf of an overdue user is blocked too`() {
        service.borrowBook(admin, dune.id, alice.id, -1)

        assertFailsWith<LoanExceptions.BorrowerHasOverdueLoansException> { service.borrowBook(admin, dune.id, alice.id, 14) }
    }

    @Test
    fun `another account's overdue loan does not block`() {
        service.borrowBook(admin, dune.id, bob.id, -1)

        service.borrowBook(asAlice, dune.id, null, 14)

        assertEquals(2, loanCount())
    }
}
