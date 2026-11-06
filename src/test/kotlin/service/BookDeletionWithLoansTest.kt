package org.example.service

import org.example.database.DatabaseFactory
import org.example.http.BookHandler
import org.example.http.anAdmin
import org.example.http.asActor
import org.example.http.bookRoutes
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookExceptions
import org.example.model.Borrower
import org.example.model.Loan
import org.example.model.User
import org.example.model.toBorrower
import org.example.repository.HookedBookRepository
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import org.example.repository.SQLiteAuthorRepository
import org.example.repository.SQLiteBookRepository
import org.example.repository.SQLiteLoanRepository
import org.example.repository.SQLiteUserRepository
import org.http4k.core.Method.DELETE
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CONFLICT
import java.io.File
import java.time.LocalDate
import kotlin.test.*

class BookDeletionWithLoansTest {

    private val today = LocalDate.now()

    // ─── in-memory: the service rule ─────────────────────────────────────────

    private val books = InMemoryBookRepository()
    private val loans = InMemoryLoanRepository()
    private val service = BookService(books, InMemoryAuthorRepository(), loans)
    private val dune = books.save(Book(0, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, 3))
    private val alice = Borrower(1, "Alice", "alice@example.com")

    private fun lend(book: Book, returned: Boolean = false) =
        loans.save(Loan(book = book, borrower = alice, borrowedAt = today, dueDate = today, returnedAt = if (returned) today else null))

    @Test
    fun `a book with an active loan cannot be deleted`() {
        lend(dune)

        assertFailsWith<BookExceptions.BookHasLoansException> { service.removeBook(dune.id) }
        assertEquals(dune, books.findById(dune.id))
    }

    @Test
    fun `a book whose loans are all returned still cannot be deleted`() {
        lend(dune, returned = true)

        assertFailsWith<BookExceptions.BookHasLoansException> { service.removeBook(dune.id) }
        assertEquals(dune, books.findById(dune.id))
    }

    @Test
    fun `a book without loans is deleted`() {
        assertTrue(service.removeBook(dune.id))
        assertNull(books.findById(dune.id))
    }

    @Test
    fun `DELETE of a book with loans is 409`() {
        lend(dune)

        val response = bookRoutes(BookHandler(service)).asActor(anAdmin)(Request(DELETE, "/books/${dune.id}"))

        assertEquals(CONFLICT, response.status)
    }

    // ─── SQLite: the foreign key catches a loan that races in ────────────────

    @Test
    fun `a loan created between the check and the delete makes the delete fail with BookHasLoans`() {
        val dbFile = File.createTempFile("book-deletion", ".db").apply { deleteOnExit() }
        try {
            val database = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
            val sqlBooks = HookedBookRepository(SQLiteBookRepository(database))
            val sqlLoans = SQLiteLoanRepository(database)
            val book = sqlBooks.save(Book(0, "Dune", SQLiteAuthorRepository(database).save(Author(name = "Frank Herbert")), BookCategory.SCIENCE_FICTION, 3))
            val bob = SQLiteUserRepository(database).save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))
            sqlBooks.beforeNextDelete {
                sqlLoans.save(Loan(book = book, borrower = bob.toBorrower(), borrowedAt = today, dueDate = today))
            }

            assertFailsWith<BookExceptions.BookHasLoansException> {
                BookService(sqlBooks, SQLiteAuthorRepository(database), sqlLoans).removeBook(book.id)
            }
            assertEquals(book, sqlBooks.findById(book.id))
        } finally {
            dbFile.delete()
        }
    }
}
