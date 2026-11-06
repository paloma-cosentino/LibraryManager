package org.example.repository

import org.example.model.Role
import org.example.model.User
import kotlin.test.*

// Keeps the fake faithful to SQLiteUserRepository's column-disjoint role semantics.
class InMemoryUserRepositoryTest {

    private val repository = InMemoryUserRepository()

    @Test
    fun `update does not write back a stale role after updateRole`() {
        val stale = repository.save(User(name = "Ada", email = "ada@example.com", passwordHash = "h", role = Role.ADMIN))
        repository.updateRole(stale.id, Role.USER)

        val returned = repository.update(stale.copy(name = "Ada L"))

        assertEquals(Role.USER, returned?.role)
        assertEquals("Ada L", repository.findById(stale.id)?.name)
        assertEquals(Role.USER, repository.findById(stale.id)?.role)
    }

    @Test
    fun `updateRole changes only the role and reports success`() {
        val saved = repository.save(User(name = "Ada", email = "ada@example.com", passwordHash = "h"))

        assertTrue(repository.updateRole(saved.id, Role.ADMIN))

        assertEquals(saved.copy(role = Role.ADMIN), repository.findById(saved.id))
    }

    @Test
    fun `updateRole returns false when user does not exist`() {
        assertFalse(repository.updateRole(999, Role.ADMIN))
    }
}
