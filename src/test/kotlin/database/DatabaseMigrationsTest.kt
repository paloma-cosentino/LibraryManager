package org.example.database

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.*

class DatabaseMigrationsTest {

    private lateinit var dbFile: File
    private val url get() = "jdbc:sqlite:${dbFile.absolutePath}"

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("migration-test", ".db")
        dbFile.deleteOnExit()
    }

    @AfterTest
    fun tearDown() {
        dbFile.delete()
    }

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection(url).use(block)

    private fun createOldSchemaWithRows() = withConnection { connection ->
        connection.createStatement().use { statement ->
            OLD_SCHEMA.forEach(statement::executeUpdate)
            statement.executeUpdate("""INSERT INTO authors (id, "name") VALUES (1, 'Frank Herbert')""")
            statement.executeUpdate("INSERT INTO books (id, title, author_id, category, quantity, published_year, edition) VALUES (1, 'Dune', 1, 'Science Fiction', 3, 1965, NULL)")
            statement.executeUpdate("INSERT INTO books (id, title, author_id, category, quantity, published_year, edition) VALUES (2, 'Dune Messiah', 1, 'Science Fiction', 0, NULL, '2nd')")
            statement.executeUpdate("INSERT INTO loans (id, book_id, borrower_name, borrowed_at, due_date, returned_at) VALUES (1, 2, 'Alice', '2026-10-01', '2026-10-15', NULL)")
            statement.executeUpdate("INSERT INTO loans (id, book_id, borrower_name, borrowed_at, due_date, returned_at) VALUES (2, 1, 'Bob', '2026-10-02', '2026-10-16', '2026-10-10')")
            statement.executeUpdate("""INSERT INTO users (id, "name", email, password_hash) VALUES (1, 'Alice', 'alice@example.com', 'hash-a')""")
            statement.executeUpdate("""INSERT INTO users (id, "name", email, password_hash) VALUES (2, 'Bob', 'bob@example.com', 'hash-b')""")
        }
    }

    private fun rows(table: String): List<Map<String, Any?>> = withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM $table ORDER BY id").use { resultSet ->
                val columns = (1..resultSet.metaData.columnCount).map(resultSet.metaData::getColumnName)
                buildList {
                    while (resultSet.next()) add(columns.associateWith(resultSet::getObject))
                }
            }
        }
    }

    @Test
    fun `migrate adds version zero to existing books and loans and preserves rows`() {
        createOldSchemaWithRows()
        val booksBefore = rows("books")
        val loansBefore = rows("loans")

        DatabaseMigrations.migrate(url)

        assertEquals(booksBefore.map { it + ("version" to 0) }, rows("books"))
        val userIdByName = mapOf("Alice" to 1, "Bob" to 2)
        assertEquals(
            loansBefore.map { it - "borrower_name" + ("user_id" to userIdByName[it["borrower_name"]]) + ("version" to 0) },
            rows("loans")
        )
    }

    @Test
    fun `migrate is idempotent when run again`() {
        createOldSchemaWithRows()
        DatabaseMigrations.migrate(url)
        val schemaAfterFirstRun = schemaSql()
        val booksAfterFirstRun = rows("books")
        val loansAfterFirstRun = rows("loans")

        DatabaseMigrations.migrate(url)

        assertEquals(schemaAfterFirstRun, schemaSql())
        assertEquals(booksAfterFirstRun, rows("books"))
        assertEquals(loansAfterFirstRun, rows("loans"))
    }

    @Test
    fun `DatabaseFactory init migrates an old database before use`() {
        createOldSchemaWithRows()

        DatabaseFactory.init(url)

        assertEquals(listOf(0, 0), rows("books").map { it["version"] })
        assertEquals(listOf(0, 0), rows("loans").map { it["version"] })
    }

    @Test
    fun `migrated books and loans columns match a fresh database`() {
        createOldSchemaWithRows()
        DatabaseMigrations.migrate(url)
        val freshFile = File.createTempFile("fresh-test", ".db").apply { deleteOnExit() }
        try {
            val freshUrl = "jdbc:sqlite:${freshFile.absolutePath}"
            DatabaseFactory.init(freshUrl)

            // Restored in plan-2 inc 14 (was temporarily books-only in inc 13).
            listOf("books", "loans").forEach { table ->
                assertEquals(columnInfo(freshUrl, table), columnInfo(url, table), "columns of $table")
            }
        } finally {
            freshFile.delete()
        }
    }

    @Test
    fun `migrated and fresh databases enforce the same version range checks`() {
        createOldSchemaWithRows()
        DatabaseMigrations.migrate(url)
        val freshFile = File.createTempFile("fresh-test", ".db").apply { deleteOnExit() }
        try {
            val freshUrl = "jdbc:sqlite:${freshFile.absolutePath}"
            DatabaseFactory.init(freshUrl)
            DriverManager.getConnection(freshUrl).use { connection ->
                connection.createStatement().use { statement -> FRESH_ROWS.forEach(statement::executeUpdate) }
            }

            listOf(url, freshUrl).forEach { databaseUrl ->
                listOf("books", "loans").forEach { table ->
                    assertFailsWith<SQLException>("$table.version overflow accepted in $databaseUrl") {
                        DriverManager.getConnection(databaseUrl).use { connection ->
                            connection.createStatement().use {
                                it.executeUpdate("UPDATE $table SET version = 2147483648 WHERE id = 1")
                            }
                        }
                    }
                }
            }
            assertEquals(checkConstraintNames(freshUrl), checkConstraintNames(url))
        } finally {
            freshFile.delete()
        }
    }

    @Test
    fun `migrate adds role USER to existing users and preserves rows`() {
        createOldSchemaWithRows()
        val usersBefore = rows("users")

        DatabaseMigrations.migrate(url)

        assertEquals(2, usersBefore.size)
        assertEquals(usersBefore.map { it + ("role" to "USER") }, rows("users"))
    }

    @Test
    fun `migrate leaves an already migrated users table and its roles untouched`() {
        createOldSchemaWithRows()
        DatabaseMigrations.migrate(url)
        withConnection { c -> c.createStatement().use { it.executeUpdate("UPDATE users SET role = 'ADMIN' WHERE id = 1") } }
        val schemaBefore = schemaSql()
        val usersBefore = rows("users")

        DatabaseMigrations.migrate(url)

        assertEquals(schemaBefore, schemaSql())
        assertEquals(usersBefore, rows("users"))
    }

    @Test
    fun `migrated users schema matches a fresh database`() {
        createOldSchemaWithRows()
        DatabaseMigrations.migrate(url)
        val freshFile = File.createTempFile("fresh-test", ".db").apply { deleteOnExit() }
        try {
            val freshUrl = "jdbc:sqlite:${freshFile.absolutePath}"
            DatabaseFactory.init(freshUrl)

            assertEquals(columnInfo(freshUrl, "users"), columnInfo(url, "users"))
            assertEquals(checkConstraintNames(freshUrl, "users"), checkConstraintNames(url, "users"))
            assertEquals(tableSql(freshUrl, "users"), tableSql(url, "users"))
        } finally {
            freshFile.delete()
        }
    }

    private fun tableSql(databaseUrl: String, table: String): String =
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.prepareStatement("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?").use { statement ->
                statement.setString(1, table)
                statement.executeQuery().use { it.next(); it.getString("sql") }
            }
        }

    // ─── loans.borrower_name -> loans.user_id rebuild ─────────────────────────

    private data class OldLoan(val id: Int, val borrower: String, val bookId: Int = 1)

    /** Old schema with one author, books 1 and 2, the given users (id to name) and loans. */
    private fun createOldSchema(users: List<Pair<Int, String>>, loans: List<OldLoan>, databaseUrl: String = url) =
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement -> OLD_SCHEMA.forEach(statement::executeUpdate) }
            connection.createStatement().use { statement ->
                statement.executeUpdate("""INSERT INTO authors (id, "name") VALUES (1, 'Frank Herbert')""")
                statement.executeUpdate("INSERT INTO books (id, title, author_id, category, quantity) VALUES (1, 'Dune', 1, 'Science Fiction', 3)")
                statement.executeUpdate("INSERT INTO books (id, title, author_id, category, quantity) VALUES (2, 'Emma', 1, 'Fiction', 1)")
            }
            connection.prepareStatement("""INSERT INTO users (id, "name", email, password_hash) VALUES (?, ?, ?, 'secret-hash')""").use { insert ->
                users.forEach { (id, name) ->
                    insert.setInt(1, id); insert.setString(2, name); insert.setString(3, "user$id@example.com"); insert.executeUpdate()
                }
            }
            connection.prepareStatement("INSERT INTO loans (id, book_id, borrower_name, borrowed_at, due_date) VALUES (?, ?, ?, '2026-10-01', '2026-10-15')").use { insert ->
                loans.forEach { loan ->
                    insert.setInt(1, loan.id); insert.setInt(2, loan.bookId); insert.setString(3, loan.borrower); insert.executeUpdate()
                }
            }
        }

    private fun loanUserIds(): Map<Int, Int> = rows("loans").associate { (it["id"] as Int) to (it["user_id"] as Int) }

    private fun sequenceRows() = rows("(SELECT rowid AS id, name, seq FROM sqlite_sequence)")

    private fun assertRefusedAndUnchanged(vararg expectedInMessage: String, databaseUrl: String = url) {
        val schemaBefore = schemaSql()
        val loansBefore = rows("loans")
        val sequenceBefore = sequenceRows()

        val error = assertFailsWith<MigrationException> { DatabaseMigrations.migrate(databaseUrl) }

        expectedInMessage.forEach { assertTrue(it in error.message!!, "'$it' missing from: ${error.message}") }
        assertFalse("secret-hash" in error.message!! || "@example.com" in error.message!!, error.message)
        assertEquals(schemaBefore, schemaSql())
        assertEquals(loansBefore, rows("loans"))
        assertEquals(sequenceBefore, sequenceRows())
    }

    @Test
    fun `rebuild maps each borrower name to the single matching account and keeps every loan field`() {
        createOldSchema(listOf(1 to "Alice", 2 to "Bob"), listOf(OldLoan(3, "Bob", bookId = 2), OldLoan(7, "Alice")))
        // As left by plan-1 (version already present) with a non-default version to prove it is copied.
        withConnection { c ->
            c.createStatement().use {
                it.executeUpdate("ALTER TABLE loans ADD COLUMN version INT DEFAULT 0 NOT NULL")
                it.executeUpdate("UPDATE loans SET returned_at = '2026-10-09', version = 4 WHERE id = 7")
            }
        }
        val before = rows("loans")

        DatabaseMigrations.migrate(url)

        assertEquals(before.map { it - "borrower_name" + ("user_id" to if (it["borrower_name"] == "Bob") 2 else 1) }, rows("loans"))
        assertFalse("borrower_name" in columnInfo(url, "loans").map { it[0] })
    }

    @Test
    fun `rebuild matches names after NFC normalization, trimming and root-locale lowercasing`() {
        createOldSchema(
            listOf(1 to "álvaro", 2 to "Zoë"),
            listOf(OldLoan(1, "  ÁLVARO "), OldLoan(2, "Zoe\u0308"), OldLoan(3, "A\u0301lvaro"))
        )

        DatabaseMigrations.migrate(url)

        assertEquals(mapOf(1 to 1, 2 to 2, 3 to 1), loanUserIds())
    }

    @Test
    fun `rebuild refuses unmatched borrowers, naming the loans, and changes nothing`() {
        createOldSchema(listOf(1 to "Alice"), listOf(OldLoan(1, "Alice"), OldLoan(4, "Mallory"), OldLoan(9, "Trent")))

        assertRefusedAndUnchanged("loan 4", "Mallory", "loan 9", "Trent")
        assertTrue("borrower_name" in columnInfo(url, "loans").map { it[0] })
    }

    @Test
    fun `rebuild refuses ambiguous borrowers, naming the loans, and changes nothing`() {
        createOldSchema(listOf(1 to "Álvaro", 2 to " a\u0301lvaro"), listOf(OldLoan(5, "alvaro"), OldLoan(6, "ÁLVARO")))

        assertRefusedAndUnchanged("loan 6", "ÁLVARO", "loan 5", "alvaro")
    }

    @Test
    fun `rebuild refuses a loan whose book is missing, even when the URL enables foreign keys, and changes nothing`() {
        val fkUrl = "$url?foreign_keys=on"
        createOldSchema(listOf(1 to "Alice"), listOf(OldLoan(1, "Alice"), OldLoan(8, "Alice", bookId = 99)))
        DriverManager.getConnection(fkUrl).use { c ->
            c.createStatement().use { s -> s.executeQuery("PRAGMA foreign_keys").use { it.next(); assertEquals(1, it.getInt(1), "URL must enable FKs") } }
        }

        assertRefusedAndUnchanged("loan 8", databaseUrl = fkUrl)
    }

    @Test
    fun `rebuild works on a connection whose URL enables foreign keys`() {
        createOldSchema(listOf(1 to "Alice"), listOf(OldLoan(1, "Alice")))

        DatabaseMigrations.migrate("$url?foreign_keys=on")

        assertEquals(mapOf(1 to 1), loanUserIds())
    }

    @Test
    fun `rebuild preserves the autoincrement high-water mark`() {
        createOldSchema(listOf(1 to "Alice"), listOf(OldLoan(1, "Alice"), OldLoan(2, "Alice")))
        withConnection { c -> c.createStatement().use { it.executeUpdate("UPDATE sqlite_sequence SET seq = 50 WHERE name = 'loans'") } }

        DatabaseMigrations.migrate(url)

        val newId = withConnection { c ->
            c.createStatement().use { s ->
                s.executeUpdate("INSERT INTO loans (book_id, user_id, borrowed_at, due_date) VALUES (1, 1, '2026-11-01', '2026-11-15')")
                s.executeQuery("SELECT last_insert_rowid()").use { it.next(); it.getInt(1) }
            }
        }
        assertEquals(51, newId)
    }

    @Test
    fun `rebuild of an empty loans table succeeds and keeps its high-water mark`() {
        createOldSchema(listOf(1 to "Alice"), listOf(OldLoan(3, "Alice")))
        withConnection { c -> c.createStatement().use { it.executeUpdate("DELETE FROM loans") } }

        DatabaseMigrations.migrate(url)

        assertTrue(rows("loans").isEmpty())
        assertEquals(listOf(3), sequenceRows().filter { it["name"] == "loans" }.map { it["seq"] })
    }

    @Test
    fun `rebuild of an empty loans table with no users succeeds`() {
        createOldSchema(emptyList(), emptyList())

        DatabaseMigrations.migrate(url)

        assertTrue("user_id" in columnInfo(url, "loans").map { it[0] })
    }

    @Test
    fun `migrated loans table matches a fresh database exactly`() {
        createOldSchemaWithRows()
        DatabaseMigrations.migrate(url)
        val freshFile = File.createTempFile("fresh-test", ".db").apply { deleteOnExit() }
        try {
            val freshUrl = "jdbc:sqlite:${freshFile.absolutePath}"
            DatabaseFactory.init(freshUrl)

            assertEquals(columnInfo(freshUrl, "loans"), columnInfo(url, "loans"))
            assertEquals(foreignKeys(freshUrl, "loans"), foreignKeys(url, "loans"))
            assertEquals(checkConstraintNames(freshUrl, "loans"), checkConstraintNames(url, "loans"))
            assertEquals(normalizedTableSql(freshUrl, "loans"), normalizedTableSql(url, "loans"))
            assertEquals(indexSql(freshUrl, "loans"), indexSql(url, "loans"))
        } finally {
            freshFile.delete()
        }
    }

    @Test
    fun `DatabaseFactory init on an old database yields a usable account-linked loans table`() {
        createOldSchemaWithRows()
        val database = DatabaseFactory.init(url)
        val loans = org.example.repository.SQLiteLoanRepository(database)
        val users = org.example.repository.SQLiteUserRepository(database)
        val books = org.example.repository.SQLiteBookRepository(database)
        val bob = users.findById(2)!!

        val saved = loans.save(
            org.example.model.Loan(
                book = books.findById(1)!!, borrower = org.example.model.Borrower(bob.id, bob.name, bob.email),
                borrowedAt = java.time.LocalDate.of(2026, 11, 1), dueDate = java.time.LocalDate.of(2026, 11, 15)
            )
        )

        assertEquals(saved, loans.findById(saved.id))
        assertEquals("Alice", loans.findById(1)?.borrower?.name)
    }

    // SQLite rewrites a renamed table's stored SQL as CREATE TABLE "name"; quoting is not semantic.
    private fun normalizedTableSql(databaseUrl: String, table: String) =
        tableSql(databaseUrl, table).replaceFirst("CREATE TABLE \"$table\"", "CREATE TABLE $table")

    private fun indexSql(databaseUrl: String, table: String): List<String?> =
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.prepareStatement("SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? ORDER BY name").use { statement ->
                statement.setString(1, table)
                statement.executeQuery().use { buildList { while (it.next()) add(it.getString(1)) } }
            }
        }

    private fun foreignKeys(databaseUrl: String, table: String): Set<List<Any?>> =
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA foreign_key_list($table)").use { resultSet ->
                    buildSet {
                        while (resultSet.next()) add(
                            listOf("table", "from", "to", "on_update", "on_delete", "match").map(resultSet::getObject)
                        )
                    }
                }
            }
        }

    private fun checkConstraintNames(databaseUrl: String, vararg tables: String = arrayOf("books", "loans")): Set<String> =
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                val names = tables.joinToString { "'$it'" }
                statement.executeQuery("SELECT sql FROM sqlite_master WHERE name IN ($names)").use { resultSet ->
                    buildSet {
                        while (resultSet.next()) {
                            Regex("""CONSTRAINT (\w+) CHECK""").findAll(resultSet.getString("sql"))
                                .forEach { add(it.groupValues[1]) }
                        }
                    }
                }
            }
        }

    private fun columnInfo(databaseUrl: String, table: String): Set<List<Any?>> =
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info($table)").use { resultSet ->
                    buildSet {
                        while (resultSet.next()) add(
                            listOf("name", "type", "notnull", "dflt_value", "pk").map(resultSet::getObject)
                        )
                    }
                }
            }
        }

    private fun schemaSql(): List<String> = withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY name").use { resultSet ->
                buildList { while (resultSet.next()) add(resultSet.getString("sql")) }
            }
        }
    }

    private companion object {
        val FRESH_ROWS = listOf(
            """INSERT INTO authors (id, "name") VALUES (1, 'Frank Herbert')""",
            "INSERT INTO books (id, title, author_id, category, quantity) VALUES (1, 'Dune', 1, 'Science Fiction', 3)",
            """INSERT INTO users (id, "name", email, password_hash) VALUES (1, 'Alice', 'alice@example.com', 'h')""",
            "INSERT INTO loans (id, book_id, user_id, borrowed_at, due_date) VALUES (1, 1, 1, '2026-10-01', '2026-10-15')"
        )
        val OLD_SCHEMA = listOf(
            """CREATE TABLE authors (id INTEGER PRIMARY KEY AUTOINCREMENT, "name" VARCHAR(255) NOT NULL, CONSTRAINT chk_authors_signed_integer_id CHECK (id BETWEEN -2147483648 AND 2147483647))""",
            """CREATE TABLE books (id INTEGER PRIMARY KEY AUTOINCREMENT, title VARCHAR(255) NOT NULL, author_id INT NOT NULL, category VARCHAR(255) NOT NULL, quantity INT NOT NULL, published_year INT NULL, edition VARCHAR(255) NULL, CONSTRAINT fk_books_author_id__id FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE RESTRICT ON UPDATE RESTRICT, CONSTRAINT chk_books_signed_integer_id CHECK (id BETWEEN -2147483648 AND 2147483647), CONSTRAINT chk_books_signed_integer_quantity CHECK (quantity BETWEEN -2147483648 AND 2147483647), CONSTRAINT chk_books_signed_integer_published_year CHECK (published_year BETWEEN -2147483648 AND 2147483647))""",
            """CREATE TABLE loans (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INT NOT NULL, borrower_name VARCHAR(255) NOT NULL, borrowed_at VARCHAR(10) NOT NULL, due_date VARCHAR(10) NOT NULL, returned_at VARCHAR(10) NULL, CONSTRAINT fk_loans_book_id__id FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE RESTRICT ON UPDATE RESTRICT, CONSTRAINT chk_loans_signed_integer_id CHECK (id BETWEEN -2147483648 AND 2147483647))""",
            """CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, "name" VARCHAR(255) NOT NULL, email VARCHAR(255) NOT NULL, password_hash VARCHAR(255) NOT NULL, CONSTRAINT chk_users_signed_integer_id CHECK (id BETWEEN -2147483648 AND 2147483647))""",
            """CREATE UNIQUE INDEX users_email ON users (email)"""
        )
    }
}
