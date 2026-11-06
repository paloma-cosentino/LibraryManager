package org.example.service

import org.example.model.Actor
import org.example.model.AuthorizationExceptions.ForbiddenException
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

class LoanServiceReadAuthorizationTest {

    private val books = InMemoryBookRepository()
    private val users = InMemoryUserRepository()
    private val service = LoanService(books, InMemoryLoanRepository(), users)

    private val dune = books.save(Book(0, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, 5))
    private val alice = users.save(User(name = "Alice", email = "alice@example.com", passwordHash = "h"))
    private val bob = users.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))
    private val asAlice = Actor(alice.id, Role.USER)
    private val admin = Actor(1001, Role.ADMIN)

    private val alicesLoan = service.borrowBook(admin, dune.id, alice.id, 14)
    private val bobsLoan = service.borrowBook(admin, dune.id, bob.id, 14)

    private fun filter(userId: Int? = null, name: String? = null) = LoanFilter(name, null, false, userId = userId)

    @Test
    fun `USER listing loans sees only their own`() {
        assertEquals(listOf(alicesLoan.id), service.getAllLoans(asAlice, filter()).data.map { it.id })
    }

    @Test
    fun `USER asking for another userId still sees only their own`() {
        assertEquals(listOf(alicesLoan.id), service.getAllLoans(asAlice, filter(userId = bob.id)).data.map { it.id })
    }

    @Test
    fun `USER name search cannot reach other borrowers`() {
        assertTrue(service.getAllLoans(asAlice, filter(name = "Bob")).data.isEmpty())
    }

    @Test
    fun `ADMIN sees all loans and can filter by exact userId or by name`() {
        assertEquals(setOf(alicesLoan.id, bobsLoan.id), service.getAllLoans(admin, filter()).data.map { it.id }.toSet())
        assertEquals(listOf(bobsLoan.id), service.getAllLoans(admin, filter(userId = bob.id)).data.map { it.id })
        assertEquals(listOf(bobsLoan.id), service.getAllLoans(admin, filter(name = "bo")).data.map { it.id })
    }

    @Test
    fun `USER can get their own loan but not someone else's`() {
        assertEquals(alicesLoan.id, service.getLoanById(asAlice, alicesLoan.id).id)
        assertFailsWith<ForbiddenException> { service.getLoanById(asAlice, bobsLoan.id) }
    }

    @Test
    fun `missing loan is not found for USER and ADMIN`() {
        assertFailsWith<LoanExceptions.LoanNotFoundException> { service.getLoanById(asAlice, 999) }
        assertFailsWith<LoanExceptions.LoanNotFoundException> { service.getLoanById(admin, 999) }
    }

    @Test
    fun `ADMIN can get any loan`() {
        assertEquals(bobsLoan.id, service.getLoanById(admin, bobsLoan.id).id)
    }
}
