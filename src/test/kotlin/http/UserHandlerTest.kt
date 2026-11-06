package org.example.http

import org.example.model.LoginResponse
import org.example.model.PagedResult
import org.example.model.Role
import org.example.model.UserResponse
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
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.format.Jackson.auto
import org.http4k.routing.routes
import kotlin.test.*

class UserHandlerTest {

    private lateinit var app: HttpHandler
    private lateinit var authApp: HttpHandler
    private lateinit var repository: InMemoryUserRepository
    private val userResponseLens  = Body.auto<UserResponse>().toLens()
    private val pagedUsersLens    = Body.auto<PagedResult<UserResponse>>().toLens()
    private val loginResponseLens = Body.auto<LoginResponse>().toLens()

    @BeforeTest
    fun setUp() {
        repository = InMemoryUserRepository()
        val service = UserService(repository)
        val handler = UserHandler(service)
        val public  = publicUserRoutes(handler)
        val protected = userRoutes(handler)
        authApp = public
        app     = routes(public, protected)
    }


    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun postUser(name: String = "Alice", email: String = "alice@example.com", password: String = "senha123") =
        authApp(
            Request(POST, "/users")
                .header("Content-Type", "application/json")
                .body("""{"id":0,"name":"$name","email":"$email","passwordHash":"$password"}""")
        )

    private fun postLogin(email: String, password: String) =
        authApp(
            Request(POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"email":"$email","password":"$password"}""")
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

    @Test
    fun `GET users never exposes passwordHash in the response body`() {
        postUser("Alice", "alice@example.com", "senha123")

        val response = app(Request(GET, "/users"))

        assertFalse(response.bodyString().contains("passwordHash"))
    }

    // ─── GET /users/email ─────────────────────────────────────────────────────

    @Test
    fun `GET users by email returns 200 with correct user`() {
        postUser("Alice", "alice@example.com")

        val response = app(Request(GET, "/users/email?email=alice@example.com"))

        assertEquals(OK, response.status)
        assertEquals("alice@example.com", userResponseLens(response).email)
    }

    @Test
    fun `GET users by email never exposes passwordHash in the response body`() {
        postUser("Alice", "alice@example.com", "senha123")

        val response = app(Request(GET, "/users/email?email=alice@example.com"))

        assertFalse(response.bodyString().contains("passwordHash"))
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
        val user = userResponseLens(response)
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
        val created  = userResponseLens(postUser())
        val response = app(
            Request(PUT, "/users/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"name":"Alice Updated","email":"updated@example.com"}""")
        )

        assertEquals(OK, response.status)
        val updated = userResponseLens(response)
        assertEquals("Alice Updated", updated.name)
        assertEquals("updated@example.com", updated.email)
    }

    @Test
    fun `PUT users never exposes passwordHash in the response body`() {
        val created  = userResponseLens(postUser())
        val response = app(
            Request(PUT, "/users/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"name":"Alice Updated","email":"updated@example.com"}""")
        )

        assertFalse(response.bodyString().contains("passwordHash"))
    }

    @Test
    fun `PUT users does not change the password`() {
        val created = userResponseLens(postUser("Alice", "alice@example.com", "senha123"))
        app(
            Request(PUT, "/users/${created.id}")
                .header("Content-Type", "application/json")
                .body("""{"name":"Alice Updated","email":"alice@example.com"}""")
        )

        val response = postLogin("alice@example.com", "senha123")

        assertEquals(OK, response.status)
    }

    @Test
    fun `PUT users returns 404 when user does not exist`() {
        val response = app(
            Request(PUT, "/users/999")
                .header("Content-Type", "application/json")
                .body("""{"name":"Ghost","email":"ghost@example.com"}""")
        )

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── PATCH /users/{id}/password ───────────────────────────────────────────

    @Test
    fun `PATCH users password returns 204 and allows login with the new password`() {
        val created = userResponseLens(postUser("Alice", "alice@example.com", "senha123"))

        val changeResponse = app(
            Request(PATCH, "/users/${created.id}/password")
                .header("Content-Type", "application/json")
                .body("""{"currentPassword":"senha123","newPassword":"novaSenha456"}""")
        )

        assertEquals(NO_CONTENT, changeResponse.status)
        assertEquals(OK, postLogin("alice@example.com", "novaSenha456").status)
    }

    @Test
    fun `PATCH users password returns 401 when current password is wrong`() {
        val created = userResponseLens(postUser("Alice", "alice@example.com", "senha123"))

        val response = app(
            Request(PATCH, "/users/${created.id}/password")
                .header("Content-Type", "application/json")
                .body("""{"currentPassword":"wrongPassword","newPassword":"novaSenha456"}""")
        )

        assertEquals(UNAUTHORIZED, response.status)
    }

    @Test
    fun `PATCH users password returns 404 when user does not exist`() {
        val response = app(
            Request(PATCH, "/users/999/password")
                .header("Content-Type", "application/json")
                .body("""{"currentPassword":"whatever","newPassword":"novaSenha456"}""")
        )

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── DELETE /users/{id} ───────────────────────────────────────────────────

    @Test
    fun `DELETE users returns 204 when user is deleted`() {
        val created  = userResponseLens(postUser())
        val response = app(Request(DELETE, "/users/${created.id}"))

        assertEquals(NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE users returns 404 when user does not exist`() {
        val response = app(Request(DELETE, "/users/999"))

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── POST /auth/login ─────────────────────────────────────────────────────

    @Test
    fun `POST auth login returns 200 and token when credentials are valid`() {
        postUser("Alice", "alice@example.com", "senha123")

        val response = postLogin("alice@example.com", "senha123")

        assertEquals(OK, response.status)
        val loginResponse = loginResponseLens(response)
        assertTrue(loginResponse.token.isNotBlank())
        assertEquals("alice@example.com", loginResponse.user.email)
    }

    @Test
    fun `POST auth login returns 401 when password is wrong`() {
        postUser("Alice", "alice@example.com", "senha123")

        val response = postLogin("alice@example.com", "senhaErrada")

        assertEquals(UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST auth login returns 404 when user does not exist`() {
        val response = postLogin("nobody@example.com", "senha123")

        assertEquals(NOT_FOUND, response.status)
    }

    // ─── authFilter — rotas protegidas ────────────────────────────────────────

    @Test
    fun `POST auth login returns the stored role and no passwordHash`() {
        postUser("Alice", "alice@example.com", "senha123")
        repository.updateRole(repository.findByEmail("alice@example.com")!!.id, Role.ADMIN)

        val response = postLogin("alice@example.com", "senha123")

        assertEquals(Role.ADMIN, loginResponseLens(response).user.role)
        assertFalse(response.bodyString().contains("passwordHash"))
        assertFalse(response.bodyString().contains("senha123"))
    }

    @Test
    fun `GET users items include each stored role and no passwordHash`() {
        postUser("Alice", "alice@example.com")
        postUser("Bob", "bob@example.com")
        repository.updateRole(repository.findByEmail("alice@example.com")!!.id, Role.ADMIN)

        val response = app(Request(GET, "/users"))

        assertEquals(
            mapOf("alice@example.com" to Role.ADMIN, "bob@example.com" to Role.USER),
            pagedUsersLens(response).data.associate { it.email to it.role }
        )
        assertFalse(response.bodyString().contains("passwordHash"))
    }

    @Test
    fun `POST users ignores a requested ADMIN role and creates a USER`() {
        val response = authApp(
            Request(POST, "/users")
                .header("Content-Type", "application/json")
                .body("""{"id":0,"name":"Mallory","email":"mallory@example.com","passwordHash":"senha123","role":"ADMIN"}""")
        )

        assertEquals(OK, response.status)
        assertEquals(Role.USER, repository.findByEmail("mallory@example.com")?.role)
    }

    @Test
    fun `protected route returns 401 without token`() {
        val protectedApp = authFilter(app)

        val response = protectedApp(Request(GET, "/users"))

        assertEquals(UNAUTHORIZED, response.status)
    }

    @Test
    fun `protected route returns 401 with invalid token`() {
        val protectedApp = authFilter(app)

        val response = protectedApp(
            Request(GET, "/users")
                .header("Authorization", "Bearer token.invalido.aqui")
        )

        assertEquals(UNAUTHORIZED, response.status)
    }

    @Test
    fun `protected route allows access with valid token`() {
        postUser("Alice", "alice@example.com", "senha123")
        val token = loginResponseLens(postLogin("alice@example.com", "senha123")).token

        val protectedApp = authFilter(app)
        val response = protectedApp(
            Request(GET, "/users")
                .header("Authorization", "Bearer $token")
        )

        assertEquals(OK, response.status)
    }
}
