package org.example.service

import org.example.database.DatabaseFactory
import org.example.http.UserHandler
import org.example.http.anAdmin
import org.example.http.asActor
import org.example.http.userRoutes
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Loan
import org.example.model.Role
import org.example.model.User
import org.example.model.UserExceptions
import org.example.model.toBorrower
import org.example.repository.HookedUserRepository
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

/** Against a DatabaseFactory database, so the foreign key is the backstop exactly as in the app. */
class UserDeletionWithLoansTest {

    private val dbFile = File.createTempFile("user-deletion", ".db").apply { deleteOnExit() }
    private val database = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    private val users = HookedUserRepository(SQLiteUserRepository(database))
    private val loans = SQLiteLoanRepository(database)
    private val service = UserService(users, loans)

    private val dune = SQLiteBookRepository(database).save(
        Book(0, "Dune", SQLiteAuthorRepository(database).save(Author(name = "Frank Herbert")), BookCategory.SCIENCE_FICTION, 3)
    )

    @AfterTest
    fun tearDown() { dbFile.delete() }

    private fun save(name: String, role: Role) =
        users.save(User(name = name, email = "$name@example.com", passwordHash = "h", role = role))

    private fun lendTo(user: User) =
        loans.save(Loan(book = dune, borrower = user.toBorrower(), borrowedAt = LocalDate.now(), dueDate = LocalDate.now()))

    @Test
    fun `DELETE of a user with loans is 409 and the user remains`() {
        save("root", Role.ADMIN)
        val bob = save("bob", Role.USER)
        lendTo(bob)

        val response = userRoutes(UserHandler(service)).asActor(anAdmin)(Request(DELETE, "/users/${bob.id}"))

        assertEquals(CONFLICT, response.status)
        assertEquals(bob, users.findById(bob.id))
    }

    @Test
    fun `a loan created between the check and the delete makes the delete fail with UserHasLoans`() {
        save("root", Role.ADMIN)
        val bob = save("bob", Role.USER)
        users.beforeNextDelete { lendTo(bob) }

        assertFailsWith<UserExceptions.UserHasLoansException> { service.removeUser(bob.id) }

        assertEquals(bob, users.findById(bob.id))
    }

    @Test
    fun `that race on an admin also restores the ADMIN role`() {
        save("root", Role.ADMIN)
        val other = save("other", Role.ADMIN)
        users.beforeNextDelete { lendTo(other) }

        assertFailsWith<UserExceptions.UserHasLoansException> { service.removeUser(other.id) }

        assertEquals(other, users.findById(other.id))
    }
}
