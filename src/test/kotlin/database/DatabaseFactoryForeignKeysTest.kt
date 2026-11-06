package org.example.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.SQLException
import kotlin.test.*

class DatabaseFactoryForeignKeysTest {

    private val dbFile = File.createTempFile("fk-test", ".db").apply { deleteOnExit() }
    private val database: Database = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")

    init {
        exec(
            """INSERT INTO authors (id, "name") VALUES (1, 'Frank Herbert')""",
            "INSERT INTO books (id, title, author_id, category, quantity) VALUES (1, 'Dune', 1, 'Science Fiction', 3)",
            """INSERT INTO users (id, "name", email, password_hash) VALUES (1, 'Alice', 'alice@example.com', 'h')""",
            "INSERT INTO loans (id, book_id, user_id, borrowed_at, due_date) VALUES (1, 1, 1, '2026-10-01', '2026-10-15')"
        )
    }

    @AfterTest
    fun tearDown() { dbFile.delete() }

    private fun exec(vararg sql: String) = transaction(database) { sql.forEach { exec(it) } }

    private fun assertRejected(sql: String) {
        // Exposed may retry a failed statement before rethrowing; the end result must still be a failure.
        assertFails { exec(sql) }.also { failure ->
            assertTrue(generateSequence(failure) { it.cause }.any { it is SQLException }, "$failure")
        }
    }

    @Test
    fun `foreign keys are on inside an app transaction`() {
        val enabled = transaction(database) { exec("PRAGMA foreign_keys") { it.next(); it.getInt(1) } }
        assertEquals(1, enabled)
    }

    @Test
    fun `deleting a book referenced by a loan is rejected`() {
        assertRejected("DELETE FROM books WHERE id = 1")
    }

    @Test
    fun `deleting a user referenced by a loan is rejected`() {
        assertRejected("DELETE FROM users WHERE id = 1")
    }

    @Test
    fun `deleting an author referenced by a book is rejected`() {
        assertRejected("DELETE FROM authors WHERE id = 1")
    }

    @Test
    fun `inserting a loan for a missing user or book is rejected`() {
        assertRejected("INSERT INTO loans (book_id, user_id, borrowed_at, due_date) VALUES (1, 999, '2026-10-01', '2026-10-15')")
        assertRejected("INSERT INTO loans (book_id, user_id, borrowed_at, due_date) VALUES (999, 1, '2026-10-01', '2026-10-15')")
    }
}
