package org.example.database

import org.jetbrains.exposed.sql.Table

object BookTable: Table("books") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 255)
    val author = varchar("author", 255)
    val category = varchar("category", 255)
    val quantity = integer("quantity")

    override val primaryKey = PrimaryKey(id)
}