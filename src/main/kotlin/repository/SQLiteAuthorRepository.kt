package org.example.repository

import org.example.database.AuthorTable
import org.example.model.Author
import org.example.model.AuthorFilter
import org.example.model.PagedResult
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.ceil

class SQLiteAuthorRepository(private val database: Database) : AuthorRepository {

    private fun ResultRow.toAuthor() = Author(
        id   = this[AuthorTable.id],
        name = this[AuthorTable.name]
    )



    override fun findAll(filter: AuthorFilter): PagedResult<Author> = transaction(database) {
        var query = AuthorTable.selectAll()

        filter.name?.let { query = query.andWhere { AuthorTable.name like "%$it%" } }

        val total      = query.count().toInt()
        val pageNum  = filter.page ?: 1
        val pageSize = filter.size ?: 10
        val offset   = ((pageNum - 1) * pageSize).toLong()
        val authors    = query.limit(pageSize).offset(start = offset).map { it.toAuthor() }
        val totalPages = if (total == 0) 0 else ceil(total.toDouble() / pageSize).toInt()

        PagedResult(
            data = authors,
            page = pageNum,
            pageSize = pageSize,
            total = total,
            totalPages = totalPages,
        )
    }

    override fun findById(id: Int): Author? = transaction(database) {
        AuthorTable.selectAll()
            .where { AuthorTable.id eq id }
            .map { it.toAuthor() }
            .firstOrNull()
    }

    override fun findByName(name: String): Author? = transaction(database) {
        AuthorTable.selectAll()
            .where { AuthorTable.name eq name }
            .map { it.toAuthor() }
            .firstOrNull()
    }

    override fun save(author: Author): Author = transaction(database) {
        author.copy(
            id = AuthorTable.insert {
                it[name] = author.name
            } get AuthorTable.id
        )
    }

    override fun update(author: Author): Author? = transaction(database) {
        val updatedRows = AuthorTable.update({ AuthorTable.id eq author.id }) {
            it[name] = author.name
        }
        if (updatedRows == 0) null else author
    }

    override fun delete(id: Int): Boolean = transaction(database) {
        AuthorTable.deleteWhere { AuthorTable.id eq id } > 0
    }
}
