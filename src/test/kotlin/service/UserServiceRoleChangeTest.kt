package org.example.service

import org.example.model.Role
import org.example.model.User
import org.example.model.UserExceptions
import org.example.repository.HookedUserRepository
import org.example.repository.InMemoryLoanRepository
import org.example.repository.InMemoryUserRepository
import kotlin.test.*

class UserServiceRoleChangeTest {

    private val repository = HookedUserRepository(InMemoryUserRepository())
    private val service = UserService(repository, InMemoryLoanRepository())

    private fun save(name: String, role: Role) =
        repository.save(User(name = name, email = "$name@example.com", passwordHash = "h", role = role))

    private fun roleOf(user: User) = repository.findById(user.id)?.role

    @Test
    fun `promotes a USER`() {
        val bob = save("bob", Role.USER)

        assertEquals(Role.ADMIN, service.changeRole(bob.id, Role.ADMIN).role)
        assertEquals(Role.ADMIN, roleOf(bob))
    }

    @Test
    fun `demotes one of two admins`() {
        val a = save("a", Role.ADMIN)
        val b = save("b", Role.ADMIN)

        assertEquals(Role.USER, service.changeRole(b.id, Role.USER).role)
        assertEquals(Role.ADMIN, roleOf(a))
        assertEquals(Role.USER, roleOf(b))
    }

    @Test
    fun `setting the current role is a no-op that writes nothing`() {
        val a = save("a", Role.ADMIN)
        repository.beforeNextRoleWrite { fail("no role write expected") }

        assertEquals(a, service.changeRole(a.id, Role.ADMIN))
    }

    @Test
    fun `demoting the only admin is rejected and the role is unchanged`() {
        val a = save("a", Role.ADMIN)
        save("u", Role.USER)

        assertFailsWith<UserExceptions.LastAdminException> { service.changeRole(a.id, Role.USER) }
        assertEquals(Role.ADMIN, roleOf(a))
    }

    @Test
    fun `unknown user is not found`() {
        assertFailsWith<UserExceptions.UserIdNotFoundException> { service.changeRole(999, Role.ADMIN) }
    }

    @Test
    fun `competing demotion that commits before our write makes ours fail, leaving one admin`() {
        val a = save("a", Role.ADMIN)
        val b = save("b", Role.ADMIN)
        repository.beforeNextRoleWrite { service.changeRole(a.id, Role.USER) }

        assertFailsWith<UserExceptions.LastAdminException> { service.changeRole(b.id, Role.USER) }

        assertEquals(Role.USER, roleOf(a))
        assertEquals(Role.ADMIN, roleOf(b))
    }

    @Test
    fun `competing demotion between our write and our count fails, ours succeeds, leaving one admin`() {
        val a = save("a", Role.ADMIN)
        val b = save("b", Role.ADMIN)
        repository.beforeNextCount {
            assertFailsWith<UserExceptions.LastAdminException> { service.changeRole(a.id, Role.USER) }
        }

        assertEquals(Role.USER, service.changeRole(b.id, Role.USER).role)

        assertEquals(Role.ADMIN, roleOf(a))
        assertEquals(Role.USER, roleOf(b))
    }
}
