package org.example.repository

import org.example.database.AuthorTable
import org.example.model.Author
import org.example.model.AuthorFilter
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.*

class SQLiteAuthorRepositoryTest {

    private lateinit var repository: SQLiteAuthorRepository
    private lateinit var database: Database
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("author-test", ".db")
        dbFile.deleteOnExit()
        database   = Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction(database) { SchemaUtils.create(AuthorTable) }
        repository = SQLiteAuthorRepository(database)
    }

    @AfterTest
    fun tearDown() { dbFile.delete() }

    // ─── save ────────────────────────────────────────────────────────────────

    @Test
    fun `save returns author with generated id`() {
        val saved = repository.save(Author(name = "Frank Herbert"))

        assertTrue(saved.id > 0)
    }

    @Test
    fun `save persists author data correctly`() {
        val saved = repository.save(Author(name = "Frank Herbert"))

        assertEquals("Frank Herbert", saved.name)
    }

    @Test
    fun `save assigns unique ids to each author`() {
        val a1 = repository.save(Author(name = "Frank Herbert"))
        val a2 = repository.save(Author(name = "Isaac Asimov"))

        assertNotEquals(a1.id, a2.id)
    }

    // ─── findById ────────────────────────────────────────────────────────────

    @Test
    fun `findById returns author when it exists`() {
        val saved = repository.save(Author(name = "Frank Herbert"))

        val found = repository.findById(saved.id)

        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("Frank Herbert", found.name)
    }

    @Test
    fun `findById returns null when author does not exist`() {
        assertNull(repository.findById(999))
    }

    // ─── findByName ──────────────────────────────────────────────────────────

    @Test
    fun `findByName returns author when name matches exactly`() {
        repository.save(Author(name = "Frank Herbert"))

        val found = repository.findByName("Frank Herbert")

        assertNotNull(found)
        assertEquals("Frank Herbert", found.name)
    }

    @Test
    fun `findByName returns null when name does not match`() {
        repository.save(Author(name = "Frank Herbert"))

        assertNull(repository.findByName("Isaac Asimov"))
    }

    // ─── findAll ─────────────────────────────────────────────────────────────

    @Test
    fun `findAll returns empty list when no authors exist`() {
        assertTrue(repository.findAll(AuthorFilter(name = null)).data.isEmpty())
    }

    @Test
    fun `findAll returns all saved authors`() {
        repository.save(Author(name = "Frank Herbert"))
        repository.save(Author(name = "Isaac Asimov"))

        assertEquals(2, repository.findAll(AuthorFilter(name = null)).data.size)
    }

    @Test
    fun `findAll filters by name (partial, case-insensitive)`() {
        repository.save(Author(name = "Frank Herbert"))
        repository.save(Author(name = "Isaac Asimov"))

        val result = repository.findAll(AuthorFilter(name = "frank")).data

        assertEquals(1, result.size)
        assertEquals("Frank Herbert", result.first().name)
    }

    @Test
    fun `findAll pagination returns correct page`() {
        repeat(5) { repository.save(Author(name = "Author $it")) }

        val page2 = repository.findAll(AuthorFilter(name = null, page = 2, size = 2))

        assertEquals(2,  page2.data.size)
        assertEquals(2,  page2.page)
        assertEquals(5,  page2.total)
        assertEquals(3,  page2.totalPages)
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    fun `update changes author name`() {
        val saved   = repository.save(Author(name = "Frank Herbert"))
        val updated = repository.update(saved.copy(name = "Frank Herbert Jr"))

        assertNotNull(updated)
        assertEquals("Frank Herbert Jr", updated.name)
    }

    @Test
    fun `update persists changes to database`() {
        val saved = repository.save(Author(name = "Frank Herbert"))
        repository.update(saved.copy(name = "Frank Herbert Jr"))

        val found = repository.findById(saved.id)

        assertEquals("Frank Herbert Jr", found?.name)
    }

    @Test
    fun `update returns null when author does not exist`() {
        val result = repository.update(Author(id = 999, name = "Ghost"))

        assertNull(result)
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete removes author and returns true`() {
        val saved  = repository.save(Author(name = "Frank Herbert"))
        val result = repository.delete(saved.id)

        assertTrue(result)
        assertNull(repository.findById(saved.id))
    }

    @Test
    fun `delete returns false when author does not exist`() {
        assertFalse(repository.delete(999))
    }

    @Test
    fun `delete only removes the targeted author`() {
        val a1 = repository.save(Author(name = "Frank Herbert"))
        val a2 = repository.save(Author(name = "Isaac Asimov"))

        repository.delete(a1.id)

        assertNull(repository.findById(a1.id))
        assertNotNull(repository.findById(a2.id))
    }
}

