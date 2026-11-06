package org.example.service

import org.example.database.DatabaseFactory
import org.example.model.Actor
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookExceptions
import org.example.model.LoanFilter
import org.example.model.Role
import org.example.model.User
import org.example.model.UserExceptions
import org.example.repository.HookedLoanRepository
import org.example.repository.SQLiteAuthorRepository
import org.example.repository.SQLiteBookRepository
import org.example.repository.SQLiteLoanRepository
import org.example.repository.SQLiteUserRepository
import java.io.File
import kotlin.test.*

/** The user or book disappears after borrowBook's checks, so the loan insert hits the foreign key. */
class LoanServiceReferenceRaceTest {

    private val dbFile = File.createTempFile("borrow-race", ".db").apply { deleteOnExit() }
    private val database = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    private val books = SQLiteBookRepository(database)
    private val users = SQLiteUserRepository(database)
    private val loans = HookedLoanRepository(SQLiteLoanRepository(database))
    private val service = LoanService(books, loans, users)
    private val admin = Actor(1001, Role.ADMIN)

    private val dune = books.save(Book(0, "Dune", SQLiteAuthorRepository(database).save(Author(name = "Frank Herbert")), BookCategory.SCIENCE_FICTION, 3))
    private val bob = users.save(User(name = "Bob", email = "bob@example.com", passwordHash = "h"))

    @AfterTest
    fun tearDown() { dbFile.delete() }

    private fun noLoans() = loans.findAll(LoanFilter(null, null, false)).data.isEmpty()

    @Test
    fun `user deleted before the loan insert gives not-found and the copy is put back`() {
        loans.beforeNextSave { assertTrue(users.delete(bob.id)) }

        assertFailsWith<UserExceptions.UserIdNotFoundException> { service.borrowBook(admin, dune.id, bob.id, 14) }

        assertEquals(3, books.findById(dune.id)?.quantity)
        assertTrue(noLoans())
    }

    @Test
    fun `book deleted before the loan insert gives book not-found`() {
        loans.beforeNextSave { assertTrue(books.delete(dune.id)) }

        assertFailsWith<BookExceptions.BookNotFoundException> { service.borrowBook(admin, dune.id, bob.id, 14) }

        assertNull(books.findById(dune.id))
        assertTrue(noLoans())
    }
}
