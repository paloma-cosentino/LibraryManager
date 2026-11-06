package org.example.repository

import org.example.database.DatabaseFactory
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Borrower
import org.example.model.Loan
import org.example.model.User
import org.example.model.toBorrower
import java.io.File
import java.time.LocalDate
import kotlin.test.*

/** Runs against a DatabaseFactory database, so foreign keys are enforced as in the app. */
class SQLiteForeignKeyTranslationTest {

    private val dbFile = File.createTempFile("fk-translation", ".db").apply { deleteOnExit() }
    private val database = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    private val authors = SQLiteAuthorRepository(database)
    private val books = SQLiteBookRepository(database)
    private val users = SQLiteUserRepository(database)
    private val loans = SQLiteLoanRepository(database)

    private val herbert = authors.save(Author(name = "Frank Herbert"))
    private val dune = books.save(Book(0, "Dune", herbert, BookCategory.SCIENCE_FICTION, 3))
    private val alice = users.save(User(name = "Alice", email = "alice@example.com", passwordHash = "h"))
    private val today = LocalDate.now()

    @AfterTest
    fun tearDown() { dbFile.delete() }

    private fun loanFor(book: Book, borrower: Borrower) = Loan(book = book, borrower = borrower, borrowedAt = today, dueDate = today)

    @Test
    fun `deleting a referenced user, book or author throws ReferencedRowException and deletes nothing`() {
        loans.save(loanFor(dune, alice.toBorrower()))

        assertFailsWith<ReferencedRowException> { users.delete(alice.id) }
        assertFailsWith<ReferencedRowException> { books.delete(dune.id) }
        assertFailsWith<ReferencedRowException> { authors.delete(herbert.id) }

        assertNotNull(users.findById(alice.id))
        assertNotNull(books.findById(dune.id))
        assertNotNull(authors.findById(herbert.id))
    }

    @Test
    fun `saving a loan for a missing user or book throws ReferencedRowException`() {
        assertFailsWith<ReferencedRowException> { loans.save(loanFor(dune, Borrower(999, "Ghost", "g@example.com"))) }
        assertFailsWith<ReferencedRowException> { loans.save(loanFor(dune.copy(id = 999), alice.toBorrower())) }
    }

    @Test
    fun `unreferenced deletes still succeed`() {
        val bob = users.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))

        assertTrue(users.delete(bob.id))
    }

    @Test
    fun `other constraint violations are not translated`() {
        val failure = assertFails { users.save(User(name = "Alice 2", email = "alice@example.com", passwordHash = "h")) }

        assertFalse(failure is ReferencedRowException, "$failure")
    }
}
