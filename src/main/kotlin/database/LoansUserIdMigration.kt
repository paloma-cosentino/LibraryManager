package org.example.database

import java.sql.Connection
import java.text.Normalizer
import java.util.Locale

/**
 * Replaces loans.borrower_name with an account-linked loans.user_id (NOT NULL, FK to users).
 *
 * SQLite cannot ALTER a column away or ADD a NOT NULL foreign-key column without a default, so the
 * table is rebuilt following SQLite's documented procedure: (with foreign keys off) create the new
 * table, copy, drop, rename, verify foreign keys — all inside DatabaseMigrations' single transaction.
 * Borrower names are matched to accounts here in Kotlin; anything not matching exactly one account
 * aborts the whole step with nothing changed — rows are never guessed at or dropped.
 */
internal object LoansUserIdMigration {

    // Exactly what Exposed generates for LoanTable on a fresh database; only the table name differs.
    private const val CREATE_NEW_TABLE =
        "CREATE TABLE loans_new (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INT NOT NULL, user_id INT NOT NULL, " +
            "borrowed_at VARCHAR(10) NOT NULL, due_date VARCHAR(10) NOT NULL, returned_at VARCHAR(10) NULL, " +
            "version INT DEFAULT 0 NOT NULL, " +
            "CONSTRAINT fk_loans_book_id__id FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE RESTRICT ON UPDATE RESTRICT, " +
            "CONSTRAINT fk_loans_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT, " +
            "CONSTRAINT chk_loans_signed_integer_id CHECK (id BETWEEN -2147483648 AND 2147483647), " +
            "CONSTRAINT chk_loans_signed_integer_version CHECK (version BETWEEN -2147483648 AND 2147483647))"

    private data class OldLoan(val id: Int, val borrowerName: String)

    fun isNeeded(connection: Connection): Boolean = "borrower_name" in connection.columnNames("loans")

    /** Runs inside the caller's transaction, on a connection with foreign keys already OFF. */
    fun apply(connection: Connection) {
        rebuild(connection, matchBorrowersToAccounts(connection))
    }

    private fun matchBorrowersToAccounts(connection: Connection): Map<Int, Int> {
        val accountsByName = readUsers(connection).entries.groupBy({ normalize(it.value) }, { it.key })
        val loans = connection.query("SELECT id, borrower_name FROM loans ORDER BY id") {
            OldLoan(it.getInt("id"), it.getString("borrower_name"))
        }
        val problems = mutableListOf<String>()
        val mapping = buildMap {
            loans.forEach { loan ->
                val candidates = accountsByName[normalize(loan.borrowerName)].orEmpty()
                when (candidates.size) {
                    1 -> put(loan.id, candidates.single())
                    0 -> problems += "loan ${loan.id} (borrower '${loan.borrowerName}'): no account with this name"
                    else -> problems += "loan ${loan.id} (borrower '${loan.borrowerName}'): ${candidates.size} accounts share this name"
                }
            }
        }
        if (problems.isNotEmpty()) throw MigrationException(
            "Cannot link loans to accounts; nothing was changed. Fix these loans or account names and restart:\n" +
                problems.joinToString("\n")
        )
        return mapping
    }

    private fun readUsers(connection: Connection): Map<Int, String> =
        if (connection.columnNames("users").isEmpty()) emptyMap()
        else connection.query("SELECT id, \"name\" FROM users") { it.getInt("id") to it.getString("name") }.toMap()

    private fun normalize(name: String) =
        Normalizer.normalize(name, Normalizer.Form.NFC).trim().lowercase(Locale.ROOT)

    private fun rebuild(connection: Connection, userIdByLoan: Map<Int, Int>) {
        val highWaterMark = connection.loansSequence()
        connection.execute(CREATE_NEW_TABLE)
        copyRows(connection, userIdByLoan)
        connection.execute("DROP TABLE loans")
        connection.execute("ALTER TABLE loans_new RENAME TO loans")
        restoreSequence(connection, highWaterMark)
        verifyForeignKeys(connection)
    }

    private data class LoanRow(
        val id: Int, val bookId: Int, val borrowedAt: String, val dueDate: String, val returnedAt: String?, val version: Int
    )

    private fun copyRows(connection: Connection, userIdByLoan: Map<Int, Int>) {
        val oldRows = connection.query("SELECT id, book_id, borrowed_at, due_date, returned_at, version FROM loans") {
            LoanRow(it.getInt("id"), it.getInt("book_id"), it.getString("borrowed_at"), it.getString("due_date"),
                it.getString("returned_at"), it.getInt("version"))
        }
        connection.prepareStatement(
            "INSERT INTO loans_new (id, book_id, user_id, borrowed_at, due_date, returned_at, version) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use { insert ->
            oldRows.forEach { row ->
                insert.setInt(1, row.id)
                insert.setInt(2, row.bookId)
                insert.setInt(3, userIdByLoan.getValue(row.id))
                insert.setString(4, row.borrowedAt)
                insert.setString(5, row.dueDate)
                insert.setString(6, row.returnedAt)
                insert.setInt(7, row.version)
                insert.executeUpdate()
            }
        }
    }

    // DROP TABLE discards the old sqlite_sequence row; ids must never be reused after deletions.
    private fun restoreSequence(connection: Connection, previous: Long?) {
        val maxId = connection.query("SELECT COALESCE(MAX(id), 0) FROM loans") { it.getLong(1) }.single()
        val target = maxOf(previous ?: 0, maxId)
        connection.execute("DELETE FROM sqlite_sequence WHERE name IN ('loans', 'loans_new')")
        if (target > 0) {
            connection.prepareStatement("INSERT INTO sqlite_sequence (name, seq) VALUES ('loans', ?)").use {
                it.setLong(1, target)
                it.executeUpdate()
            }
        }
    }

    private fun verifyForeignKeys(connection: Connection) {
        val offending = connection.query("PRAGMA foreign_key_check(loans)") { "loan ${it.getLong("rowid")} (missing ${it.getString("parent")} row)" }
        if (offending.isNotEmpty()) throw MigrationException(
            "Cannot link loans to accounts; nothing was changed. These loans reference missing rows:\n" +
                offending.joinToString("\n")
        )
    }

    private fun Connection.loansSequence(): Long? =
        if (columnNames("sqlite_sequence").isEmpty()) null
        else query("SELECT seq FROM sqlite_sequence WHERE name = 'loans'") { it.getLong(1) }.singleOrNull()

    private fun Connection.execute(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    private fun <T> Connection.query(sql: String, map: (java.sql.ResultSet) -> T): List<T> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { resultSet -> buildList { while (resultSet.next()) add(map(resultSet)) } }
        }

    // PRAGMA table_info returns no rows for a table that does not exist.
    private fun Connection.columnNames(table: String): Set<String> =
        query("PRAGMA table_info($table)") { it.getString("name") }.toSet()
}
