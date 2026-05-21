package org.example.service

import org.example.model.UserExceptions
import org.example.model.UserFilter
import org.example.repository.InMemoryUserRepository
import kotlin.test.*

class UserServiceTest {

    private lateinit var service: UserService

    @BeforeTest
    fun setUp() {
        service = UserService(InMemoryUserRepository())
    }

    // ─── getAllUsers ──────────────────────────────────────────────────────────

    @Test
    fun `getAllUsers returns empty list when no users exist`() {
        assertTrue(service.getAllUsers(UserFilter(name = null)).data.isEmpty())
    }

    @Test
    fun `getAllUsers returns all added users`() {
        service.addUser("Alice", "alice@example.com")
        service.addUser("Bob",   "bob@example.com")

        assertEquals(2, service.getAllUsers(UserFilter(name = null)).data.size)
    }

    @Test
    fun `getAllUsers filters by name`() {
        service.addUser("Alice", "alice@example.com")
        service.addUser("Bob",   "bob@example.com")

        val result = service.getAllUsers(UserFilter(name = "Ali")).data

        assertEquals(1, result.size)
        assertEquals("Alice", result.first().name)
    }

    // ─── getUserById ─────────────────────────────────────────────────────────

    @Test
    fun `getUserById returns user when it exists`() {
        val added = service.addUser("Alice", "alice@example.com")

        val found = service.getUserById(added.id)

        assertEquals(added.id, found.id)
        assertEquals("Alice", found.name)
    }

    @Test
    fun `getUserById throws UserIdNotFoundException when user does not exist`() {
        assertFailsWith<UserExceptions.UserIdNotFoundException> {
            service.getUserById(999)
        }
    }

    // ─── getUserByEmail ───────────────────────────────────────────────────────

    @Test
    fun `getUserByEmail returns user when email matches`() {
        service.addUser("Alice", "alice@example.com")

        val found = service.getUserByEmail("alice@example.com")

        assertEquals("alice@example.com", found.email)
    }

    @Test
    fun `getUserByEmail throws UserEmailNotFoundException when email does not match`() {
        assertFailsWith<UserExceptions.UserEmailNotFoundException> {
            service.getUserByEmail("nobody@example.com")
        }
    }

    // ─── addUser ─────────────────────────────────────────────────────────────

    @Test
    fun `addUser returns user with generated id`() {
        val user = service.addUser("Alice", "alice@example.com")

        assertTrue(user.id > 0)
    }

    @Test
    fun `addUser persists correct data`() {
        val user = service.addUser("Alice", "alice@example.com")

        assertEquals("Alice", user.name)
        assertEquals("alice@example.com", user.email)
    }

    @Test
    fun `addUser throws DuplicateUserException when email already exists`() {
        service.addUser("Alice", "alice@example.com")

        assertFailsWith<UserExceptions.DuplicateUserException> {
            service.addUser("Alice Clone", "alice@example.com")
        }
    }

    // ─── updateUser ──────────────────────────────────────────────────────────

    @Test
    fun `updateUser changes user data`() {
        val added   = service.addUser("Alice", "alice@example.com")
        val updated = service.updateUser(added.copy(name = "Alice Updated", email = "updated@example.com"))

        assertNotNull(updated)
        assertEquals("Alice Updated", updated.name)
        assertEquals("updated@example.com", updated.email)
    }

    @Test
    fun `updateUser persists changes so they are retrievable`() {
        val added = service.addUser("Alice", "alice@example.com")
        service.updateUser(added.copy(name = "Alice Updated", email = "updated@example.com"))

        val found = service.getUserById(added.id)

        assertEquals("Alice Updated", found.name)
    }

    @Test
    fun `updateUser returns null when user does not exist`() {
        val result = service.updateUser(org.example.model.User(id = 999, name = "Ghost", email = "ghost@example.com"))

        assertNull(result)
    }

    // ─── removeUser ──────────────────────────────────────────────────────────

    @Test
    fun `removeUser returns true and user is no longer retrievable`() {
        val added  = service.addUser("Alice", "alice@example.com")
        val result = service.removeUser(added.id)

        assertTrue(result)
        assertFailsWith<UserExceptions.UserIdNotFoundException> {
            service.getUserById(added.id)
        }
    }

    @Test
    fun `removeUser returns false when user does not exist`() {
        assertFalse(service.removeUser(999))
    }
}

