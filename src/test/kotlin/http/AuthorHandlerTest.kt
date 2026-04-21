package org.example.http

import org.example.model.Author
import org.example.model.PagedResult
import org.example.repository.InMemoryAuthorRepository
import org.example.service.AuthorService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.*
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import kotlin.test.*

class AuthorHandlerTest {

    private lateinit var app: HttpHandler
    private val authorLens       = Body.auto<Author>().toLens()
    private val pagedAuthorsLens = Body.auto<PagedResult<Author>>().toLens()

    @BeforeTest
    fun setUp() {
        val service = AuthorService(InMemoryAuthorRepository())
        val handler = AuthorHandler(service)
        app = authorRoutes(handler)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun postAuthor(name: String = "Frank Herbert") =
        app(
            Request(POST, "/authors")
                .header("Content-Type", "application/json")
                .body("""{"name":"$name"}""")
        )

    // ─── GET /authors ─────────────────────────────────────────────────────────

    @Test
    fun `GET authors returns 200 with empty list`() {
        val response = app(Request(GET, "/authors"))

        assertEquals(OK, response.status)
        assertTrue(pagedAuthorsLens(response).data.isEmpty())
    }

    @Test
    fun `GET authors returns all authors`() {
        postAuthor("Frank Herbert")
        postAuthor("Isaac Asimov")

        val response = app(Request(GET, "/authors"))

        assertEquals(OK, response.status)
        assertEquals(2, pagedAuthorsLens(response).data.size)
    }

    @Test
    fun `GET authors filters by name`() {
        postAuthor("Frank Herbert")
        postAuthor("Isaac Asimov")

        val response = app(Request(GET, "/authors?name=Frank"))
        val authors  = pagedAuthorsLens(response).data

        assertEquals(1, authors.size)
        assertEquals("Frank Herbert", authors.first().name)
    }

    // ─── GET /authors/{id} ────────────────────────────────────────────────────

    @Test
    fun `GET authors by id returns 200 with correct author`() {
        val created  = authorLens(postAuthor())
        val response = app(Request(GET, "/authors/${created.id}"))

        assertEquals(OK, response.status)
        assertEquals(created.id, authorLens(response).id)
        assertEquals("Frank Herbert", authorLens(response).name)
    }

    @Test
    fun `GET authors by id returns 404 when author does not exist`() {
        val response = app(Request(GET, "/authors/999"))

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── POST /authors ────────────────────────────────────────────────────────

    @Test
    fun `POST authors returns 200 and created author`() {
        val response = postAuthor("Frank Herbert")

        assertEquals(OK, response.status)
        assertEquals("Frank Herbert", authorLens(response).name)
    }

    @Test
    fun `POST authors assigns a generated id`() {
        val author = authorLens(postAuthor())

        assertTrue(author.id > 0)
    }

    @Test
    fun `POST authors returns 409 when author name already exists`() {
        postAuthor("Frank Herbert")
        val response = postAuthor("Frank Herbert")

        assertEquals(CONFLICT, response.status)
    }

    // ─── PUT /authors/{id} ────────────────────────────────────────────────────

    @Test
    fun `PUT authors returns 200 and updated author`() {
        val created  = authorLens(postAuthor())
        val response = app(
            Request(PUT, "/authors/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"name":"Frank Herbert Jr"}""")
        )

        assertEquals(OK, response.status)
        assertEquals("Frank Herbert Jr", authorLens(response).name)
    }

    @Test
    fun `PUT authors returns 404 when author does not exist`() {
        val response = app(
            Request(PUT, "/authors/999")
                .header("Content-Type", "application/json")
                .body("""{"name":"Ghost"}""")
        )

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── DELETE /authors/{id} ─────────────────────────────────────────────────

    @Test
    fun `DELETE authors returns 204 when author is deleted`() {
        val created  = authorLens(postAuthor())
        val response = app(Request(DELETE, "/authors/${created.id}"))

        assertEquals(NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE authors returns 404 when author does not exist`() {
        val response = app(Request(DELETE, "/authors/999"))

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `DELETE removes author so subsequent GET returns 404`() {
        val created = authorLens(postAuthor())
        app(Request(DELETE, "/authors/${created.id}"))

        val response = app(Request(GET, "/authors/${created.id}"))

        assertEquals(NOT_FOUND, response.status)
    }
}

