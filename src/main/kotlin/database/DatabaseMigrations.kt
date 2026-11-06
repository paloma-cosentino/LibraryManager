package org.example.database

import java.sql.Connection
import java.sql.DriverManager

/**
 * Explicit, ordered, idempotent schema migrations for databases created before a column existed.
 * Runs on its own plain sqlite-jdbc connection with foreign keys explicitly OFF,
 * and must run before SchemaUtils.create so that fresh databases simply skip every step.
 */
object DatabaseMigrations {
    // sqlName is the identifier exactly as Exposed writes it in CREATE TABLE, so the stored
    // table SQL of a migrated database matches a fresh one.
    private data class AddColumn(val table: String, val column: String, val definition: String, val sqlName: String = column)

    // Matches what Exposed generates for integer("version").default(0) on a fresh database,
    // including its named signed-integer range CHECK, so migrated and fresh schemas enforce the same.
    // users.role matches varchar("role", 20).default("USER"); existing accounts become USER.
    private val steps = listOf(
        AddColumn("books", "version", versionDefinition("books")),
        AddColumn("loans", "version", versionDefinition("loans")),
        AddColumn("users", "role", "VARCHAR(20) DEFAULT 'USER' NOT NULL", sqlName = "\"role\""),
    )

    private fun versionDefinition(table: String) =
        "INT DEFAULT 0 NOT NULL CONSTRAINT chk_${table}_signed_integer_version CHECK (version BETWEEN -2147483648 AND 2147483647)"

    // All steps run in one transaction: a refused or failed migration leaves the file exactly as it was.
    fun migrate(url: String) {
        DriverManager.getConnection(url).use { connection ->
            // Explicitly, whatever the URL asked for, and before the transaction starts: SQLite ignores
            // this pragma inside one. The loans rebuild drops and recreates a table with FKs.
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = OFF") }
            connection.autoCommit = false
            try {
                steps.forEach { connection.apply(it) }
                if (LoansUserIdMigration.isNeeded(connection)) LoansUserIdMigration.apply(connection)
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
    }

    private fun Connection.apply(step: AddColumn) {
        val columns = columnNames(step.table)
        if (columns.isEmpty() || step.column in columns) return
        createStatement().use {
            it.executeUpdate("ALTER TABLE ${step.table} ADD COLUMN ${step.sqlName} ${step.definition}")
        }
    }

    // PRAGMA table_info returns no rows for a table that does not exist.
    private fun Connection.columnNames(table: String): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { resultSet ->
                buildSet { while (resultSet.next()) add(resultSet.getString("name")) }
            }
        }
}
