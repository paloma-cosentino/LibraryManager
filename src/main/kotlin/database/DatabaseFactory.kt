package org.example.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        // Cria (ou abre) o arquivo library.db na raiz do projeto
        Database.connect("jdbc:sqlite:library.db", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(BookTable)  // Cria a tabela se não existir
        }
    }
}
