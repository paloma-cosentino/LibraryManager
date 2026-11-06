package org.example.http

import org.example.model.User
import org.example.repository.InMemoryUserRepository
import org.example.service.UserService
import org.http4k.core.Method.*
import org.http4k.core.Request
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.routes
import kotlin.test.*

class UserAuthorizationTest {

    private val repository = InMemoryUserRepository()
    private val handler = UserHandler(UserService(repository))
    private val routes = routes(publicUserRoutes(handler), userRoutes(handler))
    private val alice = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = "h"))

    @Test
    fun `USER cannot list users`() {
        assertEquals(FORBIDDEN, routes.asActor(aUser)(Request(GET, "/users")).status)
    }

    @Test
    fun `USER cannot delete a user, not even themself`() {
        val asAlice = routes.asActor(aUser.copy(userId = alice.id))

        assertEquals(FORBIDDEN, asAlice(Request(DELETE, "/users/${alice.id}")).status)
        assertEquals(alice, repository.findById(alice.id))
    }

    @Test
    fun `USER looking up another email gets 403 with no account details`() {
        repository.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))
        val asAlice = routes.asActor(aUser.copy(userId = alice.id))

        listOf("bob@example.com", "ghost@example.com").forEach { email ->
            val response = asAlice(Request(GET, "/users/email?email=$email"))
            assertEquals(FORBIDDEN, response.status, email)
            assertFalse(response.bodyString().contains(email), email)
        }
        assertEquals(OK, asAlice(Request(GET, "/users/email?email=alice@example.com")).status)
    }

    @Test
    fun `USER updating another profile gets 403`() {
        val bob = repository.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))
        val asAlice = routes.asActor(aUser.copy(userId = alice.id))

        val response = asAlice(Request(PUT, "/users/${bob.id}").header("Content-Type", "application/json")
            .body("""{"name":"Hacked","email":"h@example.com"}"""))

        assertEquals(FORBIDDEN, response.status)
        assertEquals(bob, repository.findById(bob.id))
    }

    @Test
    fun `ADMIN changing another user's password gets 403`() {
        val response = routes.asActor(anAdmin)(Request(PATCH, "/users/${alice.id}/password")
            .header("Content-Type", "application/json")
            .body("""{"currentPassword":"h","newPassword":"new"}"""))

        assertEquals(FORBIDDEN, response.status)
        assertEquals(alice, repository.findById(alice.id))
    }
}
