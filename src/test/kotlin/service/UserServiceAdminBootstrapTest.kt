package org.example.service

import org.example.model.Role
import org.example.model.User
import org.example.repository.InMemoryUserRepository
import kotlin.test.*

class UserServiceAdminBootstrapTest {

    private val repository = InMemoryUserRepository()
    private val service = UserService(repository)

    private fun user(email: String, role: Role = Role.USER) =
        repository.save(User(name = email.substringBefore('@'), email = email, passwordHash = "h", role = role))

    private fun roleOf(email: String) = repository.findByEmail(email)?.role

    @Test
    fun `promotes existing users by exact email`() {
        user("ada@example.com")
        user("bob@example.com")

        service.promoteAdmins(listOf("ada@example.com"))

        assertEquals(Role.ADMIN, roleOf("ada@example.com"))
        assertEquals(Role.USER, roleOf("bob@example.com"))
    }

    @Test
    fun `trims entries and ignores blanks`() {
        user("ada@example.com")

        val unknown = service.promoteAdmins(listOf("  ada@example.com ", "", "   "))

        assertEquals(Role.ADMIN, roleOf("ada@example.com"))
        assertEquals(emptyList(), unknown)
    }

    @Test
    fun `returns emails that match no user and creates no account`() {
        user("ada@example.com")

        val unknown = service.promoteAdmins(listOf("ada@example.com", "ghost@example.com", "ADA@example.com"))

        assertEquals(listOf("ghost@example.com", "ADA@example.com"), unknown)
        assertNull(repository.findByEmail("ghost@example.com"))
    }

    @Test
    fun `is idempotent`() {
        user("ada@example.com")
        service.promoteAdmins(listOf("ada@example.com"))
        val snapshot = repository.findByEmail("ada@example.com")

        val unknown = service.promoteAdmins(listOf("ada@example.com"))

        assertEquals(emptyList(), unknown)
        assertEquals(snapshot, repository.findByEmail("ada@example.com"))
    }

    @Test
    fun `never demotes admins that are not listed`() {
        user("root@example.com", Role.ADMIN)
        user("ada@example.com")

        service.promoteAdmins(listOf("ada@example.com"))
        service.promoteAdmins(emptyList())

        assertEquals(Role.ADMIN, roleOf("root@example.com"))
        assertEquals(Role.ADMIN, roleOf("ada@example.com"))
    }
}
