package org.example.database

import org.jetbrains.exposed.sql.Table

object LoanTable: Table("loans") {
    val id           = integer("id").autoIncrement()
    val bookId       = reference("book_id", BookTable.id)
    val borrowerName = varchar("borrower_name", 255)
    val borrowedAt   = varchar("borrowed_at", 10)   // ISO date: "2026-04-07"
    val dueDate      = varchar("due_date", 10)
    val returnedAt   = varchar("returned_at", 10).nullable()

    override val primaryKey = PrimaryKey(id)
}