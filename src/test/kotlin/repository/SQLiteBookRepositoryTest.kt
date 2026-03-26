package org.example.repository

import org.example.database.BookTable
import org.example.model.Book
import org.example.model.BookCategory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.*

class SQLiteBookRepositoryTest {

    private lateinit var repository: SQLiteBookRepository
    private lateinit var database: Database
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("library-test", ".db")
        dbFile.deleteOnExit()
        database = Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction(database) { SchemaUtils.create(BookTable) }
        repository = SQLiteBookRepository(database)
    }

    @AfterTest
    fun tearDown() {
        dbFile.delete()
    }

    // ─── findAll ─────────────────────────────────────────────────────────────

    @Test
    fun `findAll returns empty list when no books exist`() {
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `findAll returns all saved books`() {
        repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))
        repository.save(Book(0, "The Pragmatic Programmer", "David Thomas", BookCategory.TECHNOLOGY, 5))

        assertEquals(2, repository.findAll().size)
    }

    // ─── save ────────────────────────────────────────────────────────────────

    @Test
    fun `save returns book with generated id`() {
        val saved = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))

        assertTrue(saved.id > 0)
    }

    @Test
    fun `save persists book data correctly`() {
        val saved = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))

        assertEquals("Clean Code", saved.title)
        assertEquals("Robert C. Martin", saved.author)
        assertEquals(BookCategory.TECHNOLOGY, saved.category)
        assertEquals(3, saved.quantity)
    }

    @Test
    fun `save assigns unique ids to each book`() {
        val book1 = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))
        val book2 = repository.save(Book(0, "The Pragmatic Programmer", "David Thomas", BookCategory.TECHNOLOGY, 5))

        assertNotEquals(book1.id, book2.id)
    }

    @Test
    fun `save and findAll preserves BookCategory round-trip through database`() {
        repository.save(Book(0, "Duna", "Frank Herbert", BookCategory.SCIENCE_FICTION, 5))

        val found = repository.findAll().first()

        assertEquals(BookCategory.SCIENCE_FICTION, found.category)
    }

    // ─── findById ────────────────────────────────────────────────────────────

    @Test
    fun `findById returns book when it exists`() {
        val saved = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))

        val found = repository.findById(saved.id)

        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("Clean Code", found.title)
    }

    @Test
    fun `findById returns null when book does not exist`() {
        assertNull(repository.findById(999))
    }

    // ─── findByTitleAndAuthor ─────────────────────────────────────────────────

    @Test
    fun `findByTitleAndAuthor returns book when title and author match`() {
        repository.save(Book(0, "Duna", "Frank Herbert", BookCategory.SCIENCE_FICTION, 5))

        val found = repository.findByTitleAndAuthor("Duna", "Frank Herbert")

        assertNotNull(found)
        assertEquals("Duna", found.title)
        assertEquals("Frank Herbert", found.author)
    }

    @Test
    fun `findByTitleAndAuthor returns null when title does not match`() {
        repository.save(Book(0, "Duna", "Frank Herbert", BookCategory.SCIENCE_FICTION, 5))

        assertNull(repository.findByTitleAndAuthor("It", "Frank Herbert"))
    }

    @Test
    fun `findByTitleAndAuthor returns null when author does not match`() {
        repository.save(Book(0, "Duna", "Frank Herbert", BookCategory.SCIENCE_FICTION, 5))

        assertNull(repository.findByTitleAndAuthor("Duna", "Another Author"))
    }

    @Test
    fun `findByTitleAndAuthor distinguishes books with same title but different authors`() {
        repository.save(Book(0, "It", "Stephen King", BookCategory.HORROR, 3))
        repository.save(Book(0, "It", "Another Author", BookCategory.FICTION, 1))

        val found = repository.findByTitleAndAuthor("It", "Stephen King")

        assertNotNull(found)
        assertEquals("Stephen King", found.author)
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    fun `update changes book fields`() {
        val saved = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))

        val updated = repository.update(saved.copy(title = "Clean Code 2nd Ed", quantity = 10))

        assertNotNull(updated)
        assertEquals("Clean Code 2nd Ed", updated.title)
        assertEquals(10, updated.quantity)
    }

    @Test
    fun `update persists changes to database`() {
        val saved = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))
        repository.update(saved.copy(quantity = 10))

        val found = repository.findById(saved.id)

        assertEquals(10, found?.quantity)
    }

    @Test
    fun `update returns null when book does not exist`() {
        val nonExistent = Book(999, "Ghost Book", "Nobody", BookCategory.FICTION, 0)

        assertNull(repository.update(nonExistent))
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete removes book and returns true`() {
        val saved = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))

        val result = repository.delete(saved.id)

        assertTrue(result)
        assertNull(repository.findById(saved.id))
    }

    @Test
    fun `delete returns false when book does not exist`() {
        assertFalse(repository.delete(999))
    }

    @Test
    fun `delete only removes the targeted book`() {
        val book1 = repository.save(Book(0, "Clean Code", "Robert C. Martin", BookCategory.TECHNOLOGY, 3))
        val book2 = repository.save(Book(0, "The Pragmatic Programmer", "David Thomas", BookCategory.TECHNOLOGY, 5))

        repository.delete(book1.id)

        assertNull(repository.findById(book1.id))
        assertNotNull(repository.findById(book2.id))
    }
}
