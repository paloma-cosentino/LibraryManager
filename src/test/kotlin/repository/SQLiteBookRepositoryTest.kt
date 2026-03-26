package org.example.repository

import org.example.database.AuthorTable
import org.example.database.BookTable
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
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
        transaction(database) { SchemaUtils.create(AuthorTable, BookTable) }
        repository = SQLiteBookRepository(database)
    }

    @AfterTest
    fun tearDown() {
        dbFile.delete()
    }

    private fun insertAuthor(name: String): Author = transaction(database) {
        val id = AuthorTable.insert { it[AuthorTable.name] = name } get AuthorTable.id
        Author(id, name)
    }

    // ─── findAll ─────────────────────────────────────────────────────────────

    @Test
    fun `findAll returns empty list when no books exist`() {
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `findAll returns all saved books`() {
        val robert = insertAuthor("Robert C. Martin")
        val david  = insertAuthor("David Thomas")
        repository.save(Book(0, "Clean Code", robert, BookCategory.TECHNOLOGY, 3))
        repository.save(Book(0, "The Pragmatic Programmer", david, BookCategory.TECHNOLOGY, 5))

        assertEquals(2, repository.findAll().size)
    }

    // ─── save ────────────────────────────────────────────────────────────────

    @Test
    fun `save returns book with generated id`() {
        val author = insertAuthor("Robert C. Martin")
        val saved = repository.save(Book(0, "Clean Code", author, BookCategory.TECHNOLOGY, 3))

        assertTrue(saved.id > 0)
    }

    @Test
    fun `save persists book data correctly`() {
        val author = insertAuthor("Robert C. Martin")
        val saved = repository.save(Book(0, "Clean Code", author, BookCategory.TECHNOLOGY, 3))

        assertEquals("Clean Code", saved.title)
        assertEquals("Robert C. Martin", saved.author.name)
        assertEquals(BookCategory.TECHNOLOGY, saved.category)
        assertEquals(3, saved.quantity)
    }

    @Test
    fun `save assigns unique ids to each book`() {
        val robert = insertAuthor("Robert C. Martin")
        val david  = insertAuthor("David Thomas")
        val book1 = repository.save(Book(0, "Clean Code", robert, BookCategory.TECHNOLOGY, 3))
        val book2 = repository.save(Book(0, "The Pragmatic Programmer", david, BookCategory.TECHNOLOGY, 5))

        assertNotEquals(book1.id, book2.id)
    }

    @Test
    fun `save and findAll preserves BookCategory round-trip through database`() {
        val author = insertAuthor("Frank Herbert")
        repository.save(Book(0, "Duna", author, BookCategory.SCIENCE_FICTION, 5))

        val found = repository.findAll().first()

        assertEquals(BookCategory.SCIENCE_FICTION, found.category)
    }

    // ─── findById ────────────────────────────────────────────────────────────

    @Test
    fun `findById returns book when it exists`() {
        val author = insertAuthor("Robert C. Martin")
        val saved = repository.save(Book(0, "Clean Code", author, BookCategory.TECHNOLOGY, 3))

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
        val author = insertAuthor("Frank Herbert")
        repository.save(Book(0, "Duna", author, BookCategory.SCIENCE_FICTION, 5))

        val found = repository.findByTitleAndAuthor("Duna", author.id)

        assertNotNull(found)
        assertEquals("Duna", found.title)
        assertEquals("Frank Herbert", found.author.name)
    }

    @Test
    fun `findByTitleAndAuthor returns null when title does not match`() {
        val author = insertAuthor("Frank Herbert")
        repository.save(Book(0, "Duna", author, BookCategory.SCIENCE_FICTION, 5))

        assertNull(repository.findByTitleAndAuthor("It", author.id))
    }

    @Test
    fun `findByTitleAndAuthor returns null when author does not match`() {
        val frankHerbert   = insertAuthor("Frank Herbert")
        val anotherAuthor  = insertAuthor("Another Author")
        repository.save(Book(0, "Duna", frankHerbert, BookCategory.SCIENCE_FICTION, 5))

        assertNull(repository.findByTitleAndAuthor("Duna", anotherAuthor.id))
    }

    @Test
    fun `findByTitleAndAuthor distinguishes books with same title but different authors`() {
        val stephenKing   = insertAuthor("Stephen King")
        val anotherAuthor = insertAuthor("Another Author")
        repository.save(Book(0, "It", stephenKing, BookCategory.HORROR, 3))
        repository.save(Book(0, "It", anotherAuthor, BookCategory.FICTION, 1))

        val found = repository.findByTitleAndAuthor("It", stephenKing.id)

        assertNotNull(found)
        assertEquals("Stephen King", found.author.name)
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    fun `update changes book fields`() {
        val author = insertAuthor("Robert C. Martin")
        val saved = repository.save(Book(0, "Clean Code", author, BookCategory.TECHNOLOGY, 3))

        val updated = repository.update(saved.copy(title = "Clean Code 2nd Ed", quantity = 10))

        assertNotNull(updated)
        assertEquals("Clean Code 2nd Ed", updated.title)
        assertEquals(10, updated.quantity)
    }

    @Test
    fun `update persists changes to database`() {
        val author = insertAuthor("Robert C. Martin")
        val saved = repository.save(Book(0, "Clean Code", author, BookCategory.TECHNOLOGY, 3))
        repository.update(saved.copy(quantity = 10))

        val found = repository.findById(saved.id)

        assertEquals(10, found?.quantity)
    }

    @Test
    fun `update returns null when book does not exist`() {
        val author = insertAuthor("Nobody")
        val nonExistent = Book(999, "Ghost Book", author, BookCategory.FICTION, 0)

        assertNull(repository.update(nonExistent))
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete removes book and returns true`() {
        val author = insertAuthor("Robert C. Martin")
        val saved = repository.save(Book(0, "Clean Code", author, BookCategory.TECHNOLOGY, 3))

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
        val robert = insertAuthor("Robert C. Martin")
        val david  = insertAuthor("David Thomas")
        val book1 = repository.save(Book(0, "Clean Code", robert, BookCategory.TECHNOLOGY, 3))
        val book2 = repository.save(Book(0, "The Pragmatic Programmer", david, BookCategory.TECHNOLOGY, 5))

        repository.delete(book1.id)

        assertNull(repository.findById(book1.id))
        assertNotNull(repository.findById(book2.id))
    }
}
