package org.example.service

import org.example.model.Actor
import org.example.model.AuthorizationExceptions.ForbiddenException
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.LoanFilter
import org.example.model.Role
import org.example.model.User
import org.example.model.UserExceptions
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import org.example.repository.InMemoryUserRepository
import kotlin.test.*

class LoanServiceBorrowAuthorizationTest {

    private val books = InMemoryBookRepository()
    private val loans = InMemoryLoanRepository()
    private val users = InMemoryUserRepository()
    private val service = LoanService(books, loans, users)

    private val dune = books.save(Book(0, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, 2))
    private val alice = users.save(User(name = "Alice", email = "alice@example.com", passwordHash = "h"))
    private val bob = users.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))
    private val asAlice = Actor(alice.id, Role.USER)
    private val admin = Actor(users.save(User(name = "Root", email = "root@example.com", passwordHash = "h", role = Role.ADMIN)).id, Role.ADMIN)

    private fun assertNothingBorrowed() {
        assertEquals(2, books.findById(dune.id)?.quantity)
        assertTrue(loans.findAll(LoanFilter(null, null, false)).data.isEmpty())
    }

    @Test
    fun `USER omitting userId borrows for themself`() {
        assertEquals(alice.id, service.borrowBook(asAlice, dune.id, null, 14).borrower.id)
    }

    @Test
    fun `USER naming themself may borrow`() {
        assertEquals(alice.id, service.borrowBook(asAlice, dune.id, alice.id, 14).borrower.id)
    }

    @Test
    fun `USER naming another account is forbidden and nothing is borrowed`() {
        assertFailsWith<ForbiddenException> { service.borrowBook(asAlice, dune.id, bob.id, 14) }
        assertNothingBorrowed()
    }

    @Test
    fun `USER naming an unknown account is forbidden, not not-found`() {
        assertFailsWith<ForbiddenException> { service.borrowBook(asAlice, dune.id, 999, 14) }
        assertNothingBorrowed()
    }

    @Test
    fun `ADMIN may borrow on behalf of another account`() {
        assertEquals(bob.id, service.borrowBook(admin, dune.id, bob.id, 14).borrower.id)
    }

    @Test
    fun `ADMIN omitting userId borrows for themself`() {
        assertEquals(admin.userId, service.borrowBook(admin, dune.id, null, 14).borrower.id)
    }

    @Test
    fun `ADMIN naming an unknown account is not found and nothing is borrowed`() {
        assertFailsWith<UserExceptions.UserIdNotFoundException> { service.borrowBook(admin, dune.id, 999, 14) }
        assertNothingBorrowed()
    }
}
