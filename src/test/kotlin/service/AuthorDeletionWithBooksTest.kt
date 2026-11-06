package org.example.service

import org.example.database.DatabaseFactory
import org.example.http.AuthorHandler
import org.example.http.anAdmin
import org.example.http.asActor
import org.example.http.authorRoutes
import org.example.model.Author
import org.example.model.AuthorExceptions
import org.example.model.Book
import org.example.model.BookCategory
import org.example.repository.AuthorRepository
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import org.example.repository.SQLiteAuthorRepository
import org.example.repository.SQLiteBookRepository
import org.http4k.core.Method.DELETE
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CONFLICT
import java.io.File
import kotlin.test.*

class AuthorDeletionWithBooksTest {

    // ─── in-memory: the service rule ─────────────────────────────────────────

    private val authors = InMemoryAuthorRepository()
    private val books = InMemoryBookRepository()
    private val service = AuthorService(authors, books)
    private val herbert = authors.save(Author(name = "Frank Herbert"))

    @Test
    fun `an author with books cannot be deleted`() {
        books.save(Book(0, "Dune", herbert, BookCategory.SCIENCE_FICTION, 3))

        assertFailsWith<AuthorExceptions.AuthorHasBooksException> { service.removeAuthor(herbert.id) }
        assertEquals(herbert, authors.findById(herbert.id))
    }

    @Test
    fun `an author without books is deleted`() {
        assertTrue(service.removeAuthor(herbert.id))
        assertNull(authors.findById(herbert.id))
    }

    @Test
    fun `DELETE of an author with books is 409`() {
        books.save(Book(0, "Dune", herbert, BookCategory.SCIENCE_FICTION, 3))

        val response = authorRoutes(AuthorHandler(service)).asActor(anAdmin)(Request(DELETE, "/authors/${herbert.id}"))

        assertEquals(CONFLICT, response.status)
    }

    // ─── SQLite: the foreign key catches a book that races in ────────────────

    @Test
    fun `a book created between the check and the delete makes the delete fail with AuthorHasBooks`() {
        val dbFile = File.createTempFile("author-deletion", ".db").apply { deleteOnExit() }
        try {
            val database = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
            val sqlAuthors = SQLiteAuthorRepository(database)
            val sqlBooks = SQLiteBookRepository(database)
            val author = sqlAuthors.save(Author(name = "Frank Herbert"))
            val bookRacesIn = object : AuthorRepository by sqlAuthors {
                override fun delete(id: Int): Boolean {
                    sqlBooks.save(Book(0, "Dune", author, BookCategory.SCIENCE_FICTION, 3))
                    return sqlAuthors.delete(id)
                }
            }

            assertFailsWith<AuthorExceptions.AuthorHasBooksException> {
                AuthorService(bookRacesIn, sqlBooks).removeAuthor(author.id)
            }
            assertEquals(author, sqlAuthors.findById(author.id))
        } finally {
            dbFile.delete()
        }
    }
}
