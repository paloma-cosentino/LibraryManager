package org.example.http

import org.example.model.PagedResult
import org.example.model.User
import org.example.repository.InMemoryUserRepository
import org.example.service.UserService
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

class UserHandlerTest {

    private lateinit var app: HttpHandler
    private val userLens       = Body.auto<User>().toLens()
    private val pagedUsersLens = Body.auto<PagedResult<User>>().toLens()

    @BeforeTest
    fun setUp() {
        val service = UserService(InMemoryUserRepository())
        val handler = UserHandler(service)
        app = userRoutes(handler)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun postUser(name: String = "Alice", email: String = "alice@example.com") =
        app(
            Request(POST, "/users")
                .header("Content-Type", "application/json")
                .body("""{"id":0,"name":"$name","email":"$email"}""")
        )

    // ─── GET /users ───────────────────────────────────────────────────────────

    @Test
    fun `GET users returns 200 with empty list`() {
        val response = app(Request(GET, "/users"))

        assertEquals(OK, response.status)
        assertTrue(pagedUsersLens(response).data.isEmpty())
    }

    @Test
    fun `GET users returns all users`() {
        postUser("Alice", "alice@example.com")
        postUser("Bob",   "bob@example.com")

        val response = app(Request(GET, "/users"))

        assertEquals(OK, response.status)
        assertEquals(2, pagedUsersLens(response).data.size)
    }

    @Test
    fun `GET users filters by name`() {
        postUser("Alice", "alice@example.com")
        postUser("Bob",   "bob@example.com")

        val response = app(Request(GET, "/users?name=Ali"))
        val users    = pagedUsersLens(response).data

        assertEquals(1, users.size)
        assertEquals("Alice", users.first().name)
    }

    // ─── GET /users/email ─────────────────────────────────────────────────────

    @Test
    fun `GET users by email returns 200 with correct user`() {
        postUser("Alice", "alice@example.com")

        val response = app(Request(GET, "/users/email?email=alice@example.com"))

        assertEquals(OK, response.status)
        assertEquals("alice@example.com", userLens(response).email)
    }

    @Test
    fun `GET users by email returns 404 when user does not exist`() {
        val response = app(Request(GET, "/users/email?email=nobody@example.com"))

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `GET users by email returns 400 when email param is missing`() {
        val response = app(Request(GET, "/users/email"))

        assertEquals(org.http4k.core.Status.BAD_REQUEST, response.status)
    }

    // ─── POST /users ──────────────────────────────────────────────────────────

    @Test
    fun `POST users returns 200 and created user`() {
        val response = postUser("Alice", "alice@example.com")

        assertEquals(OK, response.status)
        val user = userLens(response)
        assertEquals("Alice", user.name)
        assertEquals("alice@example.com", user.email)
    }

    @Test
    fun `POST users returns 409 when email already exists`() {
        postUser("Alice", "alice@example.com")
        val response = postUser("Alice Clone", "alice@example.com")

        assertEquals(CONFLICT, response.status)
    }

    // ─── PUT /users/{id} ──────────────────────────────────────────────────────

    @Test
    fun `PUT users returns 200 and updated user`() {
        val created  = userLens(postUser())
        val response = app(
            Request(PUT, "/users/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"id":0,"name":"Alice Updated","email":"updated@example.com"}""")
        )

        assertEquals(OK, response.status)
        val updated = userLens(response)
        assertEquals("Alice Updated", updated.name)
        assertEquals("updated@example.com", updated.email)
    }

    @Test
    fun `PUT users returns 404 when user does not exist`() {
        val response = app(
            Request(PUT, "/users/999")
                .header("Content-Type", "application/json")
                .body("""{"id":0,"name":"Ghost","email":"ghost@example.com"}""")
        )

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── DELETE /users/{id} ───────────────────────────────────────────────────

    @Test
    fun `DELETE users returns 204 when user is deleted`() {
        val created  = userLens(postUser())
        val response = app(Request(DELETE, "/users/${created.id}"))

        assertEquals(NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE users returns 404 when user does not exist`() {
        val response = app(Request(DELETE, "/users/999"))

        assertEquals(NOT_FOUND, response.status)
    }
}

