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
        service.addUser("Alice", "alice@example.com", "senha123")
        service.addUser("Bob",   "bob@example.com", "senha123")

        assertEquals(2, service.getAllUsers(UserFilter(name = null)).data.size)
    }

    @Test
    fun `getAllUsers filters by name`() {
        service.addUser("Alice", "alice@example.com", "senha123")
        service.addUser("Bob",   "bob@example.com", "senha123")

        val result = service.getAllUsers(UserFilter(name = "Ali")).data

        assertEquals(1, result.size)
        assertEquals("Alice", result.first().name)
    }

    // ─── getUserById ─────────────────────────────────────────────────────────

    @Test
    fun `getUserById returns user when it exists`() {
        val added = service.addUser("Alice", "alice@example.com", "senha123")

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
        service.addUser("Alice", "alice@example.com", "senha123")

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
        val user = service.addUser("Alice", "alice@example.com", "senha123")

        assertTrue(user.id > 0)
    }

    @Test
    fun `addUser persists correct data`() {
        val user = service.addUser("Alice", "alice@example.com", "senha123")

        assertEquals("Alice", user.name)
        assertEquals("alice@example.com", user.email)
    }

    @Test
    fun `addUser throws DuplicateUserException when email already exists`() {
        service.addUser("Alice", "alice@example.com", "senha123")

        assertFailsWith<UserExceptions.DuplicateUserException> {
            service.addUser("Alice Clone", "alice@example.com", "senha123")
        }
    }

    // ─── updateUser ──────────────────────────────────────────────────────────

    @Test
    fun `updateUser changes user data`() {
        val added   = service.addUser("Alice", "alice@example.com", "senha123")
        val updated = service.updateUser(added.copy(name = "Alice Updated", email = "updated@example.com"))

        assertNotNull(updated)
        assertEquals("Alice Updated", updated.name)
        assertEquals("updated@example.com", updated.email)
    }

    @Test
    fun `updateUser persists changes so they are retrievable`() {
        val added = service.addUser("Alice", "alice@example.com", "senha123")
        service.updateUser(added.copy(name = "Alice Updated", email = "updated@example.com"))

        val found = service.getUserById(added.id)

        assertEquals("Alice Updated", found.name)
    }

    @Test
    fun `updateUser returns null when user does not exist`() {
        val result = service.updateUser(org.example.model.User(id = 999, name = "Ghost", email = "ghost@example.com", passwordHash = ""))

        assertNull(result)
    }

    // ─── removeUser ──────────────────────────────────────────────────────────

    @Test
    fun `removeUser returns true and user is no longer retrievable`() {
        val added  = service.addUser("Alice", "alice@example.com", "senha123")
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

    // ─── login ───────────────────────────────────────────────────────────────

    @Test
    fun `login returns LoginResponse with token when credentials are valid`() {
        service.addUser("Alice", "alice@example.com", "senha123")

        val response = service.login("alice@example.com", "senha123")

        assertNotNull(response.token)
        assertTrue(response.token.isNotBlank())
        assertEquals("alice@example.com", response.user.email)
        assertEquals("Alice", response.user.name)
    }

    @Test
    fun `login throws InvalidCredentialsException when password is wrong`() {
        service.addUser("Alice", "alice@example.com", "senha123")

        assertFailsWith<UserExceptions.InvalidCredentialsException> {
            service.login("alice@example.com", "senhaErrada")
        }
    }

    @Test
    fun `login throws UserEmailNotFoundException when email does not exist`() {
        assertFailsWith<UserExceptions.UserEmailNotFoundException> {
            service.login("nobody@example.com", "senha123")
        }
    }

    @Test
    fun `login token is different for different users`() {
        service.addUser("Alice", "alice@example.com", "senha123")
        service.addUser("Bob", "bob@example.com", "senha456")

        val tokenAlice = service.login("alice@example.com", "senha123").token
        val tokenBob   = service.login("bob@example.com", "senha456").token

        assertNotEquals(tokenAlice, tokenBob)
    }

    @Test
    fun `addUser stores passwordHash not plain text`() {
        val user = service.addUser("Alice", "alice@example.com", "senha123")

        assertNotEquals("senha123", user.passwordHash)
        assertTrue(user.passwordHash.startsWith("\$2a\$"))
    }
}

