package org.example.http

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.PagedResult
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
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
        val service = BookService(InMemoryBookRepository(), authorRepository)
        val handler = BookHandler(service)
        app = bookRoutes(handler)
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
    fun `PUT books returns 200 and updated book`() {
        val created = bookLens(postBook())

        val response = app(
            Request(PUT, "/books/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"title":"Duna Messiah","author":"Frank Herbert","category":"Science Fiction","quantity":10}""")
        )

        assertEquals(OK, response.status)
        val updated = bookLens(response)
        assertEquals("Duna Messiah", updated.title)
        assertEquals(10, updated.quantity)
    }

    @Test
    fun `PUT books returns 404 when book does not exist`() {
        val response = app(
            Request(PUT, "/books/999")
                .header("Content-Type", "application/json")
                .body("""{"title":"Ghost","author":"Nobody","category":"Fiction","quantity":1}""")
        )

        assertEquals(NOT_FOUND, response.status)
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
}
