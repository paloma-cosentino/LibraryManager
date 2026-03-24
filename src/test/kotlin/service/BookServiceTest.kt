package org.example.service

import org.example.model.Book
import org.example.repository.InMemoryBookRepository
import kotlin.test.*

class BookServiceTest {

    private lateinit var service: BookService

    @BeforeTest
    fun setUp() {
        service = BookService(InMemoryBookRepository())
    }

    // ─── getAllBooks ──────────────────────────────────────────────────────────

    @Test
    fun `getAllBooks returns empty list when no books exist`() {
        assertTrue(service.getAllBooks().isEmpty())
    }

    @Test
    fun `getAllBooks returns all added books`() {
        service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)
        service.addBook("The Pragmatic Programmer", "David Thomas", "Programming", 5)

        assertEquals(2, service.getAllBooks().size)
    }

    // ─── getBookById ──────────────────────────────────────────────────────────

    @Test
    fun `getBookById returns book when it exists`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)

        val found = service.getBookById(added.id)

        assertNotNull(found)
        assertEquals(added.id, found.id)
    }

    @Test
    fun `getBookById returns null when book does not exist`() {
        assertNull(service.getBookById(999))
    }

    // ─── addBook ──────────────────────────────────────────────────────────────

    @Test
    fun `addBook returns book with generated id`() {
        val book = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)

        assertTrue(book.id > 0)
    }

    @Test
    fun `addBook persists correct data`() {
        val book = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)

        assertEquals("Clean Code", book.title)
        assertEquals("Robert C. Martin", book.author)
        assertEquals("Programming", book.category)
        assertEquals(3, book.quantity)
    }

    @Test
    fun `addBook makes book retrievable afterwards`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)

        assertNotNull(service.getBookById(added.id))
    }

    @Test
    fun `addBook assigns unique ids to each book`() {
        val book1 = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)
        val book2 = service.addBook("The Pragmatic Programmer", "David Thomas", "Programming", 5)

        assertNotEquals(book1.id, book2.id)
    }

    // ─── updateBook ───────────────────────────────────────────────────────────

    @Test
    fun `updateBook changes book fields`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)

        val updated = service.updateBook(added.copy(title = "Clean Code 2nd Ed", quantity = 10))

        assertNotNull(updated)
        assertEquals("Clean Code 2nd Ed", updated.title)
        assertEquals(10, updated.quantity)
    }

    @Test
    fun `updateBook persists changes so they are retrievable`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)
        service.updateBook(added.copy(quantity = 10))

        val found = service.getBookById(added.id)

        assertEquals(10, found?.quantity)
    }

    @Test
    fun `updateBook returns null when book does not exist`() {
        val nonExistent = Book(999, "Ghost Book", "Nobody", "Unknown", 0)

        assertNull(service.updateBook(nonExistent))
    }

    // ─── removeBook ───────────────────────────────────────────────────────────

    @Test
    fun `removeBook returns true and book is no longer retrievable`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)

        val result = service.removeBook(added.id)

        assertTrue(result)
        assertNull(service.getBookById(added.id))
    }

    @Test
    fun `removeBook returns false when book does not exist`() {
        assertFalse(service.removeBook(999))
    }

    @Test
    fun `removeBook only removes the targeted book`() {
        val book1 = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)
        val book2 = service.addBook("The Pragmatic Programmer", "David Thomas", "Programming", 5)

        service.removeBook(book1.id)

        assertNull(service.getBookById(book1.id))
        assertNotNull(service.getBookById(book2.id))
    }
}