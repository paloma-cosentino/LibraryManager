package org.example.http

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Loan
import org.example.model.PagedResult
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
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

    private lateinit var dune: Book
    private lateinit var foundation: Book

    @BeforeTest
    fun setUp() {
        val author = Author(1, "Frank Herbert")
        val asimov = Author(2, "Isaac Asimov")

        val bookRepository = InMemoryBookRepository()
        dune       = bookRepository.save(Book(0, "Dune",       author, BookCategory.SCIENCE_FICTION, 2))
        foundation = bookRepository.save(Book(0, "Foundation", asimov, BookCategory.SCIENCE_FICTION, 1))

        val loanRepository = InMemoryLoanRepository()
        val service        = LoanService(bookRepository, loanRepository)
        val handler        = LoanHandler(service)
        app = loanRoutes(handler)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun postBorrow(bookId: Int = dune.id, borrower: String = "Alice", days: Int = 14) =
        app(
            Request(POST, "/loans")
                .header("Content-Type", "application/json")
                .body("""{"bookId":$bookId,"borrowerName":"$borrower","daysToReturn":$days}""")
        )

    private fun patchReturn(loanId: Int) =
        app(Request(PATCH, "/loans/$loanId/return"))

    // ─── POST /loans ──────────────────────────────────────────────────────────

    @Test
    fun `POST loans returns 201 and created loan`() {
        val response = postBorrow()

        assertEquals(CREATED, response.status)
        val loan = loanLens(response)
        assertEquals("Alice", loan.borrowerName)
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
        postBorrow(bookId = foundation.id, borrower = "Alice")
        val response = postBorrow(bookId = foundation.id, borrower = "Bob")
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
        postBorrow(borrower = "Alice")
        postBorrow(bookId = foundation.id, borrower = "Bob")

        val response = app(Request(GET, "/loans"))
        assertEquals(OK, response.status)
        assertEquals(2, pagedLoansLens(response).data.size)
    }

    @Test
    fun `GET loans filters by borrower name`() {
        postBorrow(borrower = "Alice")
        postBorrow(bookId = foundation.id, borrower = "Bob")

        val response = app(Request(GET, "/loans?borrower=Alice"))
        val loans    = pagedLoansLens(response).data

        assertEquals(1, loans.size)
        assertEquals("Alice", loans.first().borrowerName)
    }

    @Test
    fun `GET loans filters by bookId`() {
        postBorrow(bookId = dune.id,       borrower = "Alice")
        postBorrow(bookId = foundation.id, borrower = "Bob")

        val response = app(Request(GET, "/loans?bookId=${foundation.id}"))
        val loans    = pagedLoansLens(response).data

        assertEquals(1, loans.size)
        assertEquals(foundation.id, loans.first().book.id)
    }

    @Test
    fun `GET loans with onlyActive=true excludes returned loans`() {
        val loan = loanLens(postBorrow(borrower = "Alice"))
        postBorrow(bookId = foundation.id, borrower = "Bob")
        patchReturn(loan.id)

        val response = app(Request(GET, "/loans?onlyActive=true"))
        val loans    = pagedLoansLens(response).data

        assertEquals(1, loans.size)
        assertEquals("Bob", loans.first().borrowerName)
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
}
