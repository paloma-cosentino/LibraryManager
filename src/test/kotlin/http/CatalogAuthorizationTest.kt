package org.example.http

import org.example.model.Author
import org.example.model.AuthorFilter
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookFilter
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import org.example.service.AuthorService
import org.example.service.BookService
import org.http4k.core.Method.*
import org.http4k.core.Request
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.routes
import kotlin.test.*

class CatalogAuthorizationTest {

    private val authors = InMemoryAuthorRepository()
    private val books = InMemoryBookRepository()
    private val herbert = authors.save(Author(name = "Frank Herbert"))
    private val dune = books.save(Book(title = "Dune", author = herbert, category = BookCategory.SCIENCE_FICTION, quantity = 3))

    private val catalog = routes(
        bookRoutes(BookHandler(BookService(books, authors, InMemoryLoanRepository()))),
        authorRoutes(AuthorHandler(AuthorService(authors, books)))
    )

    private fun json(method: org.http4k.core.Method, uri: String, body: String) =
        Request(method, uri).header("Content-Type", "application/json").body(body)

    private val writes = listOf(
        json(POST, "/books", """{"title":"Emma","author":"Frank Herbert","category":"Fiction","quantity":1}"""),
        json(PUT, "/books/${dune.id}", """{"title":"Dune 2","author":"Frank Herbert","category":"Science Fiction"}"""),
        json(PATCH, "/books/${dune.id}/quantity", """{"change":-1}"""),
        Request(DELETE, "/books/${dune.id}"),
        json(POST, "/authors", """{"name":"Jane Austen"}"""),
        json(PUT, "/authors/${herbert.id}", """{"name":"F. Herbert"}"""),
        Request(DELETE, "/authors/${herbert.id}"),
    )

    private val reads = listOf(
        Request(GET, "/books"), Request(GET, "/books/${dune.id}"),
        Request(GET, "/authors"), Request(GET, "/authors/${herbert.id}"),
    )

    private fun snapshot() = books.findAll(BookFilter(null, null, null, size = 100)).data to
        authors.findAll(AuthorFilter(null, size = 100)).data

    @Test
    fun `USER gets 403 for every catalog write and nothing changes`() {
        val before = snapshot()

        writes.forEach { request ->
            assertEquals(FORBIDDEN, catalog.asActor(aUser)(request).status, "${request.method} ${request.uri}")
        }

        assertEquals(before, snapshot())
    }

    @Test
    fun `USER can read books and authors`() {
        reads.forEach { request ->
            assertEquals(OK, catalog.asActor(aUser)(request).status, "${request.method} ${request.uri}")
        }
    }

    @Test
    fun `ADMIN is not forbidden from catalog writes`() {
        writes.forEach { request ->
            assertNotEquals(FORBIDDEN, catalog.asActor(anAdmin)(request).status, "${request.method} ${request.uri}")
        }
    }
}
