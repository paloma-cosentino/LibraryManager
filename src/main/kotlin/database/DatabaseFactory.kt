package org.example.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(): Database {
        // Cria (ou abre) o arquivo library.db na raiz do projeto
        val database = Database.connect("jdbc:sqlite:library.db", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(AuthorTable, BookTable, LoanTable, UserTable)  // Cria as tabelas se não existirem
        }
        return database
    }
}
