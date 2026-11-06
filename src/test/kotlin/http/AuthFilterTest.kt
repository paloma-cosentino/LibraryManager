package org.example.http

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.example.model.Actor
import org.example.model.Role
import org.example.model.User
import org.example.repository.InMemoryUserRepository
import org.example.security.JwtService
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import java.util.Date
import kotlin.test.*

class AuthFilterTest {

    private val users = InMemoryUserRepository()
    private var seenActor: Actor? = null
    private var seenContextHeader: String? = null

    private val downstream: HttpHandler = { request ->
        seenActor = actorLens(request)
        seenContextHeader = request.header("x-http4k-context")
        Response(OK)
    }

    private val app = ServerFilters.InitialiseRequestContext(requestContexts)
        .then(authFilter(users))
        .then(downstream)

    private val alice = users.save(User(name = "Alice", email = "alice@example.com", passwordHash = "h"))

    private fun get(token: String?) =
        app(Request(GET, "/x").let { if (token == null) it else it.header("Authorization", "Bearer $token") })

    @Test
    fun `valid token gives downstream the actor with the role stored in the database`() {
        val response = get(JwtService.generateToken(alice.id, alice.email))

        assertEquals(OK, response.status)
        assertEquals(Actor(alice.id, Role.USER), seenActor)
    }

    @Test
    fun `role change after token issue is seen on the next request`() {
        val token = JwtService.generateToken(alice.id, alice.email)
        users.updateRole(alice.id, Role.ADMIN)

        get(token)

        assertEquals(Actor(alice.id, Role.ADMIN), seenActor)
    }

    @Test
    fun `role claim in the token is ignored`() {
        val token = JWT.create().withClaim("userId", alice.id).withClaim("role", "ADMIN")
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000)).sign(JwtService.algorithm)

        get(token)

        assertEquals(Role.USER, seenActor?.role)
    }

    @Test
    fun `token of a deleted user is 401`() {
        val token = JwtService.generateToken(alice.id, alice.email)
        users.delete(alice.id)

        assertUnauthorized(get(token))
    }

    @Test
    fun `malformed token is 401`() = assertUnauthorized(get("not.a.jwt"))

    @Test
    fun `token signed with another secret is 401`() {
        val forged = JWT.create().withClaim("userId", alice.id).sign(Algorithm.HMAC256("other-secret"))

        assertUnauthorized(get(forged))
    }

    @Test
    fun `expired token is 401`() {
        val expired = JWT.create().withClaim("userId", alice.id)
            .withExpiresAt(Date(System.currentTimeMillis() - 60_000)).sign(JwtService.algorithm)

        assertUnauthorized(get(expired))
    }

    @Test
    fun `missing Authorization header is 401`() = assertUnauthorized(get(null))

    @Test
    fun `non Bearer Authorization header is 401`() {
        val token = JwtService.generateToken(alice.id, alice.email)

        assertUnauthorized(app(Request(GET, "/x").header("Authorization", "Basic $token")))
    }

    @Test
    fun `token without userId claim is 401`() {
        val token = JWT.create().withClaim("email", alice.email)
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000)).sign(JwtService.algorithm)

        assertUnauthorized(get(token))
    }

    @Test
    fun `token with non integer userId claim is 401`() {
        val token = JWT.create().withClaim("userId", "1")
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000)).sign(JwtService.algorithm)

        assertUnauthorized(get(token))
    }

    @Test
    fun `client supplied request context id is replaced`() {
        val spoofed = "00000000-0000-0000-0000-000000000001"

        app(Request(GET, "/x").header("x-http4k-context", spoofed)
            .header("Authorization", "Bearer ${JwtService.generateToken(alice.id, alice.email)}"))

        assertNotNull(seenContextHeader)
        assertNotEquals(spoofed, seenContextHeader)
    }

    private fun assertUnauthorized(response: Response) {
        assertEquals(UNAUTHORIZED, response.status)
        assertNull(seenActor, "downstream must not run")
    }
}
