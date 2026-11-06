package org.example.http

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.PagedResult
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import org.example.service.BookService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.*
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import kotlin.test.*

class BookHandlerTest {

    private lateinit var app: HttpHandler
    private val bookLens       = Body.auto<Book>().toLens()
    private val pagedBooksLens = Body.auto<PagedResult<Book>>().toLens()

    @BeforeTest
    fun setUp() {
        val authorRepository = InMemoryAuthorRepository().apply {
            save(Author(name = "Frank Herbert"))
            save(Author(name = "George Orwell"))
            save(Author(name = "Nobody"))
        }
        val service = BookService(InMemoryBookRepository(), authorRepository, InMemoryLoanRepository())
        val handler = BookHandler(service)
        app = bookRoutes(handler).asActor(anAdmin)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun postBook(
        title: String    = "Duna",
        author: String   = "Frank Herbert",
        category: String = "Science Fiction",
        quantity: Int    = 5
    ) = app(
        Request(POST, "/books")
            .header("Content-Type", "application/json")
            .body("""{"title":"$title","author":"$author","category":"$category","quantity":$quantity}""")
    )

    // ─── GET /books ───────────────────────────────────────────────────────────

    @Test
    fun `GET books returns 200 with empty list`() {
        val response = app(Request(GET, "/books"))

        assertEquals(OK, response.status)
        assertTrue(pagedBooksLens(response).data.isEmpty())
    }

    @Test
    fun `GET books returns 200 with all books`() {
        postBook(title = "Duna")
        postBook(title = "1984", author = "George Orwell", category = "Fiction")

        val response = app(Request(GET, "/books"))

        assertEquals(OK, response.status)
        assertEquals(2, pagedBooksLens(response).data.size)
    }

    // ─── GET /books/{id} ──────────────────────────────────────────────────────

    @Test
    fun `GET books by id returns 200 with correct book`() {
        val created = bookLens(postBook())

        val response = app(Request(GET, "/books/${created.id}"))

        assertEquals(OK, response.status)
        assertEquals(created.id, bookLens(response).id)
        assertEquals("Duna", bookLens(response).title)
    }

    @Test
    fun `GET books by id returns 404 when book does not exist`() {
        val response = app(Request(GET, "/books/999"))

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── POST /books ──────────────────────────────────────────────────────────

    @Test
    fun `POST books returns 200 and created book`() {
        val response = postBook()

        assertEquals(OK, response.status)
        val book = bookLens(response)
        assertEquals("Duna", book.title)
        assertEquals("Frank Herbert", book.author.name)
        assertEquals(BookCategory.SCIENCE_FICTION, book.category)
        assertEquals(5, book.quantity)
    }


    @Test
    fun `POST books returns 409 when book is duplicate`() {
        postBook()
        val response = postBook()

        assertEquals(CONFLICT, response.status)
    }

    @Test
    fun `POST books returns 400 when category is invalid`() {
        val response = postBook(category = "Programming")

        assertEquals(BAD_REQUEST, response.status)
    }

    @Test
    fun `POST books returns 400 when quantity is negative`() {
        val response = postBook(quantity = -1)

        assertEquals(BAD_REQUEST, response.status)
    }

    // ─── PUT /books/{id} ──────────────────────────────────────────────────────

    @Test
    fun `PUT books returns 200 and updated details with unchanged quantity`() {
        val created = bookLens(postBook())

        val response = app(
            Request(PUT, "/books/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"title":"Duna Messiah","author":"Frank Herbert","category":"Science Fiction"}""")
        )

        assertEquals(OK, response.status)
        val updated = bookLens(response)
        assertEquals("Duna Messiah", updated.title)
        assertEquals(5, updated.quantity)
    }

    @Test
    fun `PUT books returns 400 directing to PATCH when quantity is sent`() {
        val created = bookLens(postBook())

        val response = app(
            Request(PUT, "/books/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"title":"Duna Messiah","author":"Frank Herbert","category":"Science Fiction","quantity":10}""")
        )

        assertEquals(BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("PATCH /books/{id}/quantity"))
        assertEquals(5, bookLens(app(Request(GET, "/books/${created.id}"))).quantity)
        assertEquals("Duna", bookLens(app(Request(GET, "/books/${created.id}"))).title)
    }

    @Test
    fun `PUT books returns 404 when book does not exist`() {
        val response = app(
            Request(PUT, "/books/999")
                .header("Content-Type", "application/json")
                .body("""{"title":"Ghost","author":"Nobody","category":"Fiction"}""")
        )

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── PATCH /books/{id}/quantity ───────────────────────────────────────────

    private fun patchQuantity(id: Int, body: String) = app(
        Request(PATCH, "/books/$id/quantity")
            .header("Content-Type", "application/json")
            .body(body)
    )

    @Test
    fun `PATCH quantity returns 200 and the adjusted book`() {
        val created = bookLens(postBook(quantity = 5))

        val response = patchQuantity(created.id, """{"change":-2}""")

        assertEquals(OK, response.status)
        assertEquals(3, bookLens(response).quantity)
    }

    @Test
    fun `PATCH quantity returns 409 when the change would go below zero`() {
        val created = bookLens(postBook(quantity = 1))

        assertEquals(CONFLICT, patchQuantity(created.id, """{"change":-2}""").status)
    }

    @Test
    fun `PATCH quantity returns 200 for a zero change`() {
        val created = bookLens(postBook(quantity = 5))

        val response = patchQuantity(created.id, """{"change":0}""")

        assertEquals(OK, response.status)
        assertEquals(5, bookLens(response).quantity)
    }

    @Test
    fun `PATCH quantity returns 404 when book does not exist`() {
        assertEquals(NOT_FOUND, patchQuantity(999, """{"change":1}""").status)
    }

    @Test
    fun `PATCH quantity returns 400 for a missing or invalid body`() {
        val created = bookLens(postBook())

        assertEquals(BAD_REQUEST, patchQuantity(created.id, """{}""").status)
        assertEquals(BAD_REQUEST, patchQuantity(created.id, """{"change":"lots"}""").status)
        assertEquals(BAD_REQUEST, patchQuantity(created.id, "not json").status)
    }

    @Test
    fun `PATCH quantity returns 400 when the result would overflow`() {
        val created = bookLens(postBook(quantity = Int.MAX_VALUE))

        assertEquals(BAD_REQUEST, patchQuantity(created.id, """{"change":1}""").status)
    }

    // ─── DELETE /books/{id} ───────────────────────────────────────────────────

    @Test
    fun `DELETE books returns 204 when book is deleted`() {
        val created = bookLens(postBook())

        val response = app(Request(DELETE, "/books/${created.id}"))

        assertEquals(NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE books returns 404 when book does not exist`() {
        val response = app(Request(DELETE, "/books/999"))

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `DELETE removes book so subsequent GET returns 404`() {
        val created = bookLens(postBook())
        app(Request(DELETE, "/books/${created.id}"))

        val response = app(Request(GET, "/books/${created.id}"))

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `DELETE books returns 400 when id is not a number`() {
        val response = app(Request(DELETE, "/books/abc"))

        assertEquals(BAD_REQUEST, response.status)
    }
}
