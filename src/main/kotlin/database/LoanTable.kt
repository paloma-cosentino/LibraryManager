package org.example.database

import org.jetbrains.exposed.sql.Table

object LoanTable: Table("loans") {
    val id           = integer("id").autoIncrement()
    val bookId       = reference("book_id", BookTable.id)
    val userId       = reference("user_id", UserTable.id)
    val borrowedAt   = varchar("borrowed_at", 10)   // ISO date: "2026-04-07"
    val dueDate      = varchar("due_date", 10)
    val returnedAt   = varchar("returned_at", 10).nullable()
    val version      = integer("version").default(0)

    override val primaryKey = PrimaryKey(id)
}