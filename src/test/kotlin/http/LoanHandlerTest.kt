package org.example.http

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Loan
import org.example.model.PagedResult
import org.example.model.User
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import org.example.repository.InMemoryUserRepository
import org.example.service.LoanService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.*
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import kotlin.test.*

class LoanHandlerTest {

    private lateinit var app: HttpHandler
    private val loanLens       = Body.auto<Loan>().toLens()
    private val pagedLoansLens = Body.auto<PagedResult<Loan>>().toLens()

    private lateinit var bookRepository: InMemoryBookRepository
    private lateinit var dune: Book
    private lateinit var foundation: Book
    private lateinit var alice: User
    private lateinit var bob: User

    @BeforeTest
    fun setUp() {
        val author = Author(1, "Frank Herbert")
        val asimov = Author(2, "Isaac Asimov")

        bookRepository = InMemoryBookRepository()
        dune       = bookRepository.save(Book(0, "Dune",       author, BookCategory.SCIENCE_FICTION, 2))
        foundation = bookRepository.save(Book(0, "Foundation", asimov, BookCategory.SCIENCE_FICTION, 1))

        val userRepository = InMemoryUserRepository()
        alice = userRepository.save(User(name = "Alice", email = "alice@example.com", passwordHash = "alice-secret-hash"))
        bob   = userRepository.save(User(name = "Bob",   email = "bob@example.com",   passwordHash = "bob-secret-hash"))

        val loanRepository = InMemoryLoanRepository()
        val service        = LoanService(bookRepository, loanRepository, userRepository)
        val handler        = LoanHandler(service)
        app = loanRoutes(handler).asActor(anAdmin)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun postBorrow(bookId: Int = dune.id, userId: Int = alice.id, days: Int = 14) =
        app(
            Request(POST, "/loans")
                .header("Content-Type", "application/json")
                .body("""{"bookId":$bookId,"userId":$userId,"daysToReturn":$days}""")
        )

    private fun patchReturn(loanId: Int) =
        app(Request(PATCH, "/loans/$loanId/return"))

    // ─── POST /loans ──────────────────────────────────────────────────────────

    @Test
    fun `POST loans returns 201 and created loan`() {
        val response = postBorrow()

        assertEquals(CREATED, response.status)
        val loan = loanLens(response)
        assertEquals("Alice", loan.borrower.name)
        assertEquals(dune.id, loan.book.id)
        assertNull(loan.returnedAt)
    }

    @Test
    fun `POST loans with negative daysToReturn returns 201 and an overdue loan`() {
        val response = postBorrow(days = -3)

        assertEquals(CREATED, response.status)
        val json = response.bodyString()
        assertEquals(true, Jackson.parse(json)["isOverdue"]?.booleanValue(), json)
        assertEquals(3, Jackson.parse(json)["daysOverdue"]?.intValue(), json)
    }

    @Test
    fun `POST loans returns 404 when book does not exist`() {
        val response = postBorrow(bookId = 999)
        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `POST loans returns 409 when book has no available copies`() {
        // foundation has quantity=1
        postBorrow(bookId = foundation.id, userId = alice.id)
        val response = postBorrow(bookId = foundation.id, userId = bob.id)
        assertEquals(CONFLICT, response.status)
    }

    // ─── GET /loans ───────────────────────────────────────────────────────────

    @Test
    fun `GET loans returns 200 with empty list`() {
        val response = app(Request(GET, "/loans"))
        assertEquals(OK, response.status)
        assertTrue(pagedLoansLens(response).data.isEmpty())
    }

    @Test
    fun `GET loans returns all loans`() {
        postBorrow(userId = alice.id)
        postBorrow(bookId = foundation.id, userId = bob.id)

        val response = app(Request(GET, "/loans"))
        assertEquals(OK, response.status)
        assertEquals(2, pagedLoansLens(response).data.size)
    }

    @Test
    fun `GET loans filters by borrower name`() {
        postBorrow(userId = alice.id)
        postBorrow(bookId = foundation.id, userId = bob.id)

        val response = app(Request(GET, "/loans?borrower=Alice"))
        val loans    = pagedLoansLens(response).data

        assertEquals(1, loans.size)
        assertEquals("Alice", loans.first().borrower.name)
    }

    @Test
    fun `GET loans filters by bookId`() {
        postBorrow(bookId = dune.id,       userId = alice.id)
        postBorrow(bookId = foundation.id, userId = bob.id)

        val response = app(Request(GET, "/loans?bookId=${foundation.id}"))
        val loans    = pagedLoansLens(response).data

        assertEquals(1, loans.size)
        assertEquals(foundation.id, loans.first().book.id)
    }

    @Test
    fun `GET loans with onlyActive=true excludes returned loans`() {
        val loan = loanLens(postBorrow(userId = alice.id))
        postBorrow(bookId = foundation.id, userId = bob.id)
        patchReturn(loan.id)

        val response = app(Request(GET, "/loans?onlyActive=true"))
        val loans    = pagedLoansLens(response).data

        assertEquals(1, loans.size)
        assertEquals("Bob", loans.first().borrower.name)
    }

    // ─── GET /loans/{id} ─────────────────────────────────────────────────────

    @Test
    fun `GET loans by id returns 200 with correct loan`() {
        val created  = loanLens(postBorrow())
        val response = app(Request(GET, "/loans/${created.id}"))

        assertEquals(OK, response.status)
        assertEquals(created.id, loanLens(response).id)
    }

    @Test
    fun `GET loans by id returns 404 when loan does not exist`() {
        val response = app(Request(GET, "/loans/999"))
        assertEquals(NOT_FOUND, response.status)
    }

    // ─── PATCH /loans/{id}/return ─────────────────────────────────────────────

    @Test
    fun `PATCH loans return sets returnedAt`() {
        val created  = loanLens(postBorrow())
        val response = patchReturn(created.id)

        assertEquals(OK, response.status)
        assertNotNull(loanLens(response).returnedAt)
    }


    @Test
    fun `PATCH loans return returns 409 when loan already returned`() {
        val created = loanLens(postBorrow())
        patchReturn(created.id)

        val response = patchReturn(created.id)
        assertEquals(CONFLICT, response.status)
    }

    @Test
    fun `PATCH loans return returns 404 when loan does not exist`() {
        val response = patchReturn(999)
        assertEquals(NOT_FOUND, response.status)
    }

    // ─── account-linked borrower ─────────────────────────────────────────────

    @Test
    fun `loan JSON carries borrower id, name and email and never the password hash`() {
        val response = postBorrow(userId = alice.id)
        val listed = app(Request(GET, "/loans"))

        val borrower = Jackson.parse(response.bodyString())["borrower"]
        assertEquals(alice.id, borrower["id"].intValue())
        assertEquals("Alice", borrower["name"].textValue())
        assertEquals("alice@example.com", borrower["email"].textValue())
        listOf(response, listed).forEach {
            assertFalse(it.bodyString().contains("passwordHash"))
            assertFalse(it.bodyString().contains("alice-secret-hash"))
        }
    }

    @Test
    fun `POST loans returns 404 for an unknown user and leaves stock unchanged`() {
        val response = postBorrow(userId = 999)

        assertEquals(NOT_FOUND, response.status)
        assertEquals(2, bookRepository.findById(dune.id)?.quantity)
    }
}
