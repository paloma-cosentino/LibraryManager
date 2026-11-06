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
        assertEquals(loansBefore.map { it + ("version" to 0) }, rows("loans"))
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
            "INSERT INTO loans (id, book_id, borrower_name, borrowed_at, due_date) VALUES (1, 1, 'Alice', '2026-10-01', '2026-10-15')"
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
