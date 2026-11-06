package org.example.service

import org.example.model.Actor
import org.example.model.AuthorizationExceptions.ForbiddenException
import org.example.model.Role
import org.example.model.User
import org.example.model.UserExceptions
import org.example.repository.InMemoryUserRepository
import kotlin.test.*

class UserServiceAuthorizationTest {

    private val repository = InMemoryUserRepository()
    private val service = UserService(repository)
    private val alice = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = "h"))
    private val bob = repository.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))
    private val asAlice = Actor(alice.id, Role.USER)
    private val admin = Actor(userId = 1001, role = Role.ADMIN)

    // ─── getUserByEmail ──────────────────────────────────────────────────────

    @Test
    fun `USER can look up their own email`() {
        assertEquals(alice, service.getUserByEmail(asAlice, "alice@example.com"))
    }

    @Test
    fun `USER looking up another existing account is forbidden`() {
        assertFailsWith<ForbiddenException> { service.getUserByEmail(asAlice, "bob@example.com") }
    }

    @Test
    fun `USER looking up an unknown email is forbidden, not not-found, so existence does not leak`() {
        assertFailsWith<ForbiddenException> { service.getUserByEmail(asAlice, "ghost@example.com") }
    }

    @Test
    fun `ADMIN can look up any account and gets not-found for unknown emails`() {
        assertEquals(bob, service.getUserByEmail(admin, "bob@example.com"))
        assertFailsWith<UserExceptions.UserEmailNotFoundException> { service.getUserByEmail(admin, "ghost@example.com") }
    }

    // ─── updateUserProfile ───────────────────────────────────────────────────

    @Test
    fun `USER can update their own profile`() {
        assertEquals("Alice L", service.updateUserProfile(asAlice, alice.id, "Alice L", "al@example.com")?.name)
    }

    @Test
    fun `USER cannot update another profile, existing or not, and nothing changes`() {
        assertFailsWith<ForbiddenException> { service.updateUserProfile(asAlice, bob.id, "Hacked", "h@example.com") }
        assertFailsWith<ForbiddenException> { service.updateUserProfile(asAlice, 999, "Hacked", "h@example.com") }
        assertEquals(bob, repository.findById(bob.id))
    }

    @Test
    fun `ADMIN can update any profile`() {
        assertEquals("Robert", service.updateUserProfile(admin, bob.id, "Robert", "bob@example.com")?.name)
    }

    // ─── changePassword ──────────────────────────────────────────────────────

    @Test
    fun `USER cannot change another user's password, existing or not`() {
        assertFailsWith<ForbiddenException> { service.changePassword(asAlice, bob.id, "h", "new") }
        assertFailsWith<ForbiddenException> { service.changePassword(asAlice, 999, "h", "new") }
        assertEquals(bob, repository.findById(bob.id))
    }

    @Test
    fun `ADMIN cannot change another user's password either`() {
        assertFailsWith<ForbiddenException> { service.changePassword(admin, bob.id, "h", "new") }
        assertEquals(bob, repository.findById(bob.id))
    }

    @Test
    fun `ADMIN changing their own password still needs the current password`() {
        val root = service.addUser("Root", "root@example.com", "senha123")
        repository.updateRole(root.id, Role.ADMIN)

        assertFailsWith<UserExceptions.InvalidCredentialsException> {
            service.changePassword(Actor(root.id, Role.ADMIN), root.id, "wrong", "new")
        }
    }
}
