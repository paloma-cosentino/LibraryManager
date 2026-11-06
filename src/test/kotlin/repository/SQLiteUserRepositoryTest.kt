package org.example.repository

import org.example.database.UserTable
import org.example.model.Role
import org.example.model.User
import org.example.model.UserFilter
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.*

class SQLiteUserRepositoryTest {

    private lateinit var repository: SQLiteUserRepository
    private lateinit var database: Database
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("user-test", ".db")
        dbFile.deleteOnExit()
        database   = Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction(database) { SchemaUtils.create(UserTable) }
        repository = SQLiteUserRepository(database)
    }

    @AfterTest
    fun tearDown() { dbFile.delete() }

    // ─── save ────────────────────────────────────────────────────────────────

    @Test
    fun `save returns user with generated id`() {
        val saved = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))

        assertTrue(saved.id > 0)
    }

    @Test
    fun `save persists user data correctly`() {
        val saved = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))

        assertEquals("Alice", saved.name)
        assertEquals("alice@example.com", saved.email)
    }

    @Test
    fun `save assigns unique ids to each user`() {
        val u1 = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))
        val u2 = repository.save(User(name = "Bob",   email = "bob@example.com", passwordHash = ""))

        assertNotEquals(u1.id, u2.id)
    }

    // ─── findById ────────────────────────────────────────────────────────────

    @Test
    fun `findById returns user when it exists`() {
        val saved = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))

        val found = repository.findById(saved.id)

        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("Alice", found.name)
    }

    @Test
    fun `findById returns null when user does not exist`() {
        assertNull(repository.findById(999))
    }

    // ─── findByEmail ─────────────────────────────────────────────────────────

    @Test
    fun `findByEmail returns user when email matches`() {
        repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))

        val found = repository.findByEmail("alice@example.com")

        assertNotNull(found)
        assertEquals("alice@example.com", found.email)
    }

    @Test
    fun `findByEmail returns null when email does not match`() {
        repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))

        assertNull(repository.findByEmail("nobody@example.com"))
    }

    // ─── findAll ─────────────────────────────────────────────────────────────

    @Test
    fun `findAll returns empty list when no users exist`() {
        assertTrue(repository.findAll(UserFilter(name = null)).data.isEmpty())
    }

    @Test
    fun `findAll returns all saved users`() {
        repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))
        repository.save(User(name = "Bob",   email = "bob@example.com", passwordHash = ""))

        assertEquals(2, repository.findAll(UserFilter(name = null)).data.size)
    }

    @Test
    fun `findAll filters by name (partial)`() {
        repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))
        repository.save(User(name = "Bob",   email = "bob@example.com", passwordHash = ""))

        val result = repository.findAll(UserFilter(name = "Ali")).data

        assertEquals(1, result.size)
        assertEquals("Alice", result.first().name)
    }

    @Test
    fun `findAll pagination returns correct page`() {
        repeat(5) { repository.save(User(name = "User $it", email = "user$it@example.com", passwordHash = "")) }

        val page2 = repository.findAll(UserFilter(name = null, page = 2, size = 2))

        assertEquals(2, page2.data.size)
        assertEquals(2, page2.page)
        assertEquals(5, page2.total)
        assertEquals(3, page2.totalPages)
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    fun `update changes user data`() {
        val saved   = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))
        val updated = repository.update(saved.copy(name = "Alice Updated", email = "updated@example.com"))

        assertNotNull(updated)
        assertEquals("Alice Updated", updated.name)
        assertEquals("updated@example.com", updated.email)
    }

    @Test
    fun `update persists changes to database`() {
        val saved = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))
        repository.update(saved.copy(name = "Alice Updated", email = "updated@example.com"))

        val found = repository.findById(saved.id)

        assertEquals("Alice Updated", found?.name)
    }

    @Test
    fun `update returns null when user does not exist`() {
        val result = repository.update(User(id = 999, name = "Ghost", email = "ghost@example.com", passwordHash = ""))

        assertNull(result)
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete removes user and returns true`() {
        val saved  = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))
        val result = repository.delete(saved.id)

        assertTrue(result)
        assertNull(repository.findById(saved.id))
    }

    @Test
    fun `delete returns false when user does not exist`() {
        assertFalse(repository.delete(999))
    }

    @Test
    fun `delete only removes the targeted user`() {
        val u1 = repository.save(User(name = "Alice", email = "alice@example.com", passwordHash = ""))
        val u2 = repository.save(User(name = "Bob",   email = "bob@example.com", passwordHash = ""))

        repository.delete(u1.id)

        assertNull(repository.findById(u1.id))
        assertNotNull(repository.findById(u2.id))
    }

    // ─── role ────────────────────────────────────────────────────────────────

    @Test
    fun `save and find round trip the role`() {
        val admin = repository.save(User(name = "Ada", email = "ada@example.com", passwordHash = "h", role = Role.ADMIN))
        val user  = repository.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))

        assertEquals(Role.ADMIN, repository.findById(admin.id)?.role)
        assertEquals(Role.ADMIN, repository.findByEmail("ada@example.com")?.role)
        assertEquals(Role.USER, repository.findById(user.id)?.role)
        assertEquals(setOf(Role.ADMIN, Role.USER), repository.findAll(UserFilter(name = null)).data.map { it.role }.toSet())
    }

    @Test
    fun `update never writes the role column`() {
        val saved = repository.save(User(name = "Ada", email = "ada@example.com", passwordHash = "h"))

        repository.update(saved.copy(name = "Ada L", role = Role.ADMIN))

        val stored = repository.findById(saved.id)!!
        assertEquals("Ada L", stored.name)
        assertEquals(Role.USER, stored.role)
    }

    @Test
    fun `update does not write back a stale role after updateRole`() {
        val stale = repository.save(User(name = "Ada", email = "ada@example.com", passwordHash = "h", role = Role.ADMIN))
        repository.updateRole(stale.id, Role.USER)

        val returned = repository.update(stale.copy(name = "Ada L"))

        assertEquals(Role.USER, returned?.role)
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

    @Test
    fun `countByRole counts only users with that role`() {
        repository.save(User(name = "A", email = "a@example.com", passwordHash = "h", role = Role.ADMIN))
        repository.save(User(name = "B", email = "b@example.com", passwordHash = "h", role = Role.ADMIN))
        repository.save(User(name = "C", email = "c@example.com", passwordHash = "h"))

        assertEquals(2, repository.countByRole(Role.ADMIN))
        assertEquals(1, repository.countByRole(Role.USER))
    }
}

