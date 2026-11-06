package org.example.http

import org.example.model.Role
import org.example.model.User
import org.example.model.UserResponse
import org.example.repository.InMemoryUserRepository
import org.example.service.UserService
import org.http4k.core.Body
import org.http4k.core.Method.PATCH
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import kotlin.test.*

class UserRoleHandlerTest {

    private val repository = InMemoryUserRepository()
    private val routes = userRoutes(UserHandler(UserService(repository)))
    private val userResponseLens = Body.auto<UserResponse>().toLens()
    private val root = repository.save(User(name = "Root", email = "root@example.com", passwordHash = "h", role = Role.ADMIN))
    private val bob = repository.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))

    private fun patchRole(id: Int, body: String, actor: org.example.model.Actor = anAdmin) =
        routes.asActor(actor)(Request(PATCH, "/users/$id/role").header("Content-Type", "application/json").body(body))

    @Test
    fun `ADMIN promotes a user and gets the updated user back`() {
        val response = patchRole(bob.id, """{"role":"ADMIN"}""")

        assertEquals(OK, response.status)
        assertEquals(Role.ADMIN, userResponseLens(response).role)
        assertFalse(response.bodyString().contains("passwordHash"))
        assertEquals(Role.ADMIN, repository.findById(bob.id)?.role)
    }

    @Test
    fun `demoting the last admin is 409 and nothing changes`() {
        assertEquals(CONFLICT, patchRole(root.id, """{"role":"USER"}""").status)
        assertEquals(Role.ADMIN, repository.findById(root.id)?.role)
    }

    @Test
    fun `invalid or missing role is 400`() {
        assertEquals(BAD_REQUEST, patchRole(bob.id, """{"role":"SUPERUSER"}""").status)
        assertEquals(BAD_REQUEST, patchRole(bob.id, """{}""").status)
        assertEquals(Role.USER, repository.findById(bob.id)?.role)
    }

    @Test
    fun `unknown user is 404`() {
        assertEquals(NOT_FOUND, patchRole(999, """{"role":"ADMIN"}""").status)
    }

    @Test
    fun `deleting the last admin is 409 and the admin remains`() {
        val response = routes.asActor(anAdmin)(Request(org.http4k.core.Method.DELETE, "/users/${root.id}"))

        assertEquals(CONFLICT, response.status)
        assertEquals(root, repository.findById(root.id))
    }

    @Test
    fun `USER is forbidden, even for their own account`() {
        assertEquals(FORBIDDEN, patchRole(bob.id, """{"role":"ADMIN"}""", aUser.copy(userId = bob.id)).status)
        assertEquals(Role.USER, repository.findById(bob.id)?.role)
    }
}
