package org.example.service

import org.example.model.Role
import org.example.model.User
import org.example.model.UserExceptions
import org.example.repository.HookedUserRepository
import org.example.repository.InMemoryUserRepository
import kotlin.test.*

class UserServiceRemoveUserTest {

    private val repository = HookedUserRepository(InMemoryUserRepository())
    private val service = UserService(repository)

    private fun save(name: String, role: Role) =
        repository.save(User(name = name, email = "$name@example.com", passwordHash = "h", role = role))

    @Test
    fun `deleting a USER works as before`() {
        save("root", Role.ADMIN)
        val bob = save("bob", Role.USER)

        assertTrue(service.removeUser(bob.id))
        assertNull(repository.findById(bob.id))
    }

    @Test
    fun `deleting an unknown user returns false`() {
        assertFalse(service.removeUser(999))
    }

    @Test
    fun `deleting the only admin is rejected and the admin still exists`() {
        val root = save("root", Role.ADMIN)

        assertFailsWith<UserExceptions.LastAdminException> { service.removeUser(root.id) }
        assertEquals(root, repository.findById(root.id))
    }

    @Test
    fun `deleting one of two admins works`() {
        val a = save("a", Role.ADMIN)
        val b = save("b", Role.ADMIN)

        assertTrue(service.removeUser(b.id))
        assertNull(repository.findById(b.id))
        assertEquals(Role.ADMIN, repository.findById(a.id)?.role)
    }

    @Test
    fun `admins deleting each other, competitor first, leaves the loser as an admin`() {
        val a = save("a", Role.ADMIN)
        val b = save("b", Role.ADMIN)
        repository.beforeNextRoleWrite { assertTrue(service.removeUser(a.id)) }

        assertFailsWith<UserExceptions.LastAdminException> { service.removeUser(b.id) }

        assertNull(repository.findById(a.id))
        assertEquals(Role.ADMIN, repository.findById(b.id)?.role)
    }

    @Test
    fun `admins deleting each other, competitor between our write and count, leaves the competitor as admin`() {
        val a = save("a", Role.ADMIN)
        val b = save("b", Role.ADMIN)
        repository.beforeNextCount {
            assertFailsWith<UserExceptions.LastAdminException> { service.removeUser(a.id) }
        }

        assertTrue(service.removeUser(b.id))

        assertNull(repository.findById(b.id))
        assertEquals(Role.ADMIN, repository.findById(a.id)?.role)
    }

    @Test
    fun `a failing delete after the demotion restores ADMIN and rethrows`() {
        val a = save("a", Role.ADMIN)
        save("b", Role.ADMIN)
        val failure = IllegalStateException("disk full")
        repository.beforeNextDelete { throw failure }

        assertSame(failure, assertFails { service.removeUser(a.id) })
        assertEquals(Role.ADMIN, repository.findById(a.id)?.role)
    }

    @Test
    fun `a delete that finds nothing after the demotion returns false`() {
        val a = save("a", Role.ADMIN)
        save("b", Role.ADMIN)
        // Competing request deletes `a` after our demotion but before our delete.
        repository.beforeNextDelete { repository.delete(a.id) }

        assertFalse(service.removeUser(a.id))
        assertEquals(1, repository.countByRole(Role.ADMIN))
    }
}
