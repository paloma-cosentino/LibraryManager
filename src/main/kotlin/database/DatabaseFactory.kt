package org.example.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(url: String = "jdbc:sqlite:library.db"): Database {
        DatabaseMigrations.migrate(url)
        // Cria (ou abre) o arquivo library.db na raiz do projeto
        val database = Database.connect(url, driver = "org.sqlite.JDBC", setupConnection = ::enforceForeignKeys)
        transaction(database) {
            SchemaUtils.create(AuthorTable, BookTable, LoanTable, UserTable)  // Cria as tabelas se não existirem
        }
        return database
    }

    // Runs on every new JDBC connection, before Exposed starts a transaction on it: SQLite ignores
    // this pragma inside a transaction, and it is per-connection, off by default.
    private fun enforceForeignKeys(connection: java.sql.Connection) {
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
    }
}
