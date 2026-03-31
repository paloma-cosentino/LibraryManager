package org.example.service

import org.example.model.BookCategory
import org.example.model.BookFilter
import org.example.model.Exceptions
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import kotlin.test.*

class BookServiceTest {

    private lateinit var service: BookService
    private lateinit var authorRepository: InMemoryAuthorRepository

    @BeforeTest
    fun setUp() {
        authorRepository = InMemoryAuthorRepository()
        service = BookService(InMemoryBookRepository(), authorRepository)
    }

    // ─── getAllBooks ──────────────────────────────────────────────────────────

    @Test
    fun `getAllBooks returns empty list when no books exist`() {
        assertTrue(service.getAllBooks(filter = BookFilter()).data.isEmpty())
    }

    @Test
    fun `getAllBooks returns all added books`() {
        service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)
        service.addBook("The Pragmatic Programmer", "David Thomas", "Technology", 5)

        assertEquals(2, service.getAllBooks(BookFilter()).data.size)
    }

    // ─── getBookById ──────────────────────────────────────────────────────────

    @Test
    fun `getBookById returns book when it exists`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        val found = service.getBookById(added.id)

        assertEquals(added.id, found.id)
    }

    @Test
    fun `getBookById throws BookNotFoundException when book does not exist`() {
        assertFailsWith<Exceptions.BookNotFoundException> {
            service.getBookById(999)
        }
    }

    // ─── addBook ──────────────────────────────────────────────────────────────

    @Test
    fun `addBook returns book with generated id`() {
        val book = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        assertTrue(book.id > 0)
    }

    @Test
    fun `addBook persists correct data`() {
        val book = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        assertEquals("Clean Code", book.title)
        assertEquals("Robert C. Martin", book.author.name)
        assertEquals(BookCategory.TECHNOLOGY, book.category)
        assertEquals(3, book.quantity)
    }

    @Test
    fun `addBook makes book retrievable afterwards`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        assertNotNull(service.getBookById(added.id))
    }

    @Test
    fun `addBook assigns unique ids to each book`() {
        val book1 = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)
        val book2 = service.addBook("The Pragmatic Programmer", "David Thomas", "Technology", 5)

        assertNotEquals(book1.id, book2.id)
    }

    @Test
    fun `addBook throws DuplicateBookException when same title and author already exist`() {
        service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        assertFailsWith<Exceptions.DuplicateBookException> {
            service.addBook("Clean Code", "Robert C. Martin", "Technology", 1)
        }
    }

    @Test
    fun `addBook allows same title with different author`() {
        service.addBook("It", "Stephen King", "Horror", 3)
        val book = service.addBook("It", "Another Author", "Fiction", 1)

        assertNotNull(book)
    }

    @Test
    fun `addBook throws InvalidCategoryException when category is not valid`() {
        assertFailsWith<Exceptions.InvalidCategoryException> {
            service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)
        }
    }

    @Test
    fun `addBook creates author automatically when author does not exist`() {
        val book = service.addBook("Duna", "Frank Herbert", "Science Fiction", 4)

        assertEquals("Frank Herbert", book.author.name)
        assertTrue(book.author.id > 0)
    }

    @Test
    fun `addBook persists new author in repository when author does not exist`() {
        service.addBook("Duna", "Frank Herbert", "Science Fiction", 4)

        assertNotNull(authorRepository.findByName("Frank Herbert"))
    }

    @Test
    fun `addBook reuses existing author and does not create duplicate`() {
        service.addBook("Duna", "Frank Herbert", "Science Fiction", 4)
        service.addBook("It", "Frank Herbert", "Horror", 2)

        val allAuthors = authorRepository.findAll().filter { it.name == "Frank Herbert" }
        assertEquals(1, allAuthors.size)
    }


    // ─── updateBook ───────────────────────────────────────────────────────────

    @Test
    fun `updateBook changes book fields`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        val updated = service.updateBook(
            id         = added.id,
            title      = "Clean Code 2nd Ed",
            authorName = added.author.name,
            category   = added.category.displayName,
            quantity   = 10
        )

        assertNotNull(updated)
        assertEquals("Clean Code 2nd Ed", updated.title)
        assertEquals(10, updated.quantity)
    }

    @Test
    fun `updateBook persists changes so they are retrievable`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)
        service.updateBook(
            id         = added.id,
            title      = added.title,
            authorName = added.author.name,
            category   = added.category.displayName,
            quantity   = 10
        )

        val found = service.getBookById(added.id)

        assertEquals(10, found.quantity)
    }

    @Test
    fun `updateBook returns null when book does not exist`() {
        assertNull(service.updateBook(999, "Ghost Book", "Robert C. Martin", "Fiction", 0))
    }

    @Test
    fun `updateBook creates author automatically when new author does not exist`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        val updated = service.updateBook(
            id         = added.id,
            title      = added.title,
            authorName = "New Unknown Author",
            category   = added.category.displayName,
            quantity   = added.quantity
        )

        assertNotNull(updated)
        assertEquals("New Unknown Author", updated.author.name)
        assertNotNull(authorRepository.findByName("New Unknown Author"))
    }

    // ─── removeBook ───────────────────────────────────────────────────────────

    @Test
    fun `removeBook returns true and book is no longer retrievable`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)

        val result = service.removeBook(added.id)

        assertTrue(result)
        assertFailsWith<Exceptions.BookNotFoundException> {
            service.getBookById(added.id)
        }
    }

    @Test
    fun `removeBook returns false when book does not exist`() {
        assertFalse(service.removeBook(999))
    }

    @Test
    fun `removeBook only removes the targeted book`() {
        val book1 = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)
        val book2 = service.addBook("The Pragmatic Programmer", "David Thomas", "Technology", 5)

        service.removeBook(book1.id)

        assertFailsWith<Exceptions.BookNotFoundException> { service.getBookById(book1.id) }
        assertNotNull(service.getBookById(book2.id))
    }
}