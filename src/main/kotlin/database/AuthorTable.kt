package org.example.database

import org.jetbrains.exposed.sql.Table

object AuthorTable: Table("authors") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}