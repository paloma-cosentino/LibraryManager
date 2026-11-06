package org.example.database

import org.jetbrains.exposed.sql.Table

object BookTable: Table("books") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 255)
    val authorId = reference("author_id", AuthorTable.id)
    val category = varchar("category", 255)
    val quantity = integer("quantity")
    val publishedYear = integer("published_year").nullable()
    val edition = varchar("edition", 255).nullable()
    val version = integer("version").default(0)

    override val primaryKey = PrimaryKey(id)
}