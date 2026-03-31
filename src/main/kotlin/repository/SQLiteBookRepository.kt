package org.example.repository

import org.example.database.AuthorTable
import org.example.database.BookTable
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookFilter
import org.example.model.PagedResult
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.ceil

class SQLiteBookRepository(private val database: Database): BookRepository {
    private fun ResultRow.toBook() = Book(
        id = this[BookTable.id],
        title = this[BookTable.title],
        author = Author(
            this[AuthorTable.id],
            this[AuthorTable.name]
        ),
        category = BookCategory.entries.first { it.displayName == this[BookTable.category] },
        quantity = this[BookTable.quantity],
        publishedYear = this[BookTable.publishedYear],
        edition = this[BookTable.edition]
    )


    override fun findAll(filter: BookFilter): PagedResult<Book> = transaction(database) {
        var query = (BookTable innerJoin AuthorTable).selectAll()

        filter.title?.let { query = query.andWhere { BookTable.title like "%$it%" } }
        filter.author?.let { query = query.andWhere { AuthorTable.name like "%$it%" } }
        filter.category?.let { query = query.andWhere { BookTable.category eq it } }

        val total = query.count().toInt()
        val pageNum = filter.page ?: 1
        val pageSize = filter.size ?: 10
        val offset = ((pageNum - 1) * pageSize).toLong()
        val books = query.limit(pageSize).offset(start = offset).map { it.toBook() }
        val totalPages = if (total == 0) 0 else ceil(total.toDouble() / pageSize).toInt()
        PagedResult(
            data = books,
            page = pageNum,
            pageSize = pageSize,
            total = total,
            totalPages = totalPages
        )
    }

    override fun findById(id: Int): Book? = transaction(database) {
        (BookTable innerJoin AuthorTable).selectAll()
             .where {BookTable.id eq id}
             .map {it.toBook()}
             .firstOrNull()
    }

    override fun findByTitleAndAuthor(title: String, authorId: Int): Book? = transaction(database) {
        (BookTable innerJoin AuthorTable).selectAll()
            .where { (BookTable.title eq title) and (BookTable.authorId eq authorId) }
            .map { it.toBook() }
            .firstOrNull()
    }

    override fun save(book: Book): Book = transaction (database){
        book.copy(
            id = BookTable.insert {
                it[title] = book.title
                it[authorId] = book.author.id
                it[category] = book.category.displayName
                it[quantity] = book.quantity
                it[publishedYear] = book.publishedYear
                it[edition] = book.edition
            } get BookTable.id
        )
    }

    override fun update(book: Book): Book? = transaction (database){
        val updatedRows = BookTable.update({ BookTable.id eq book.id }) {
            it[title] = book.title
            it[authorId] = book.author.id
            it[category] = book.category.displayName
            it[quantity] = book.quantity
            it[publishedYear] = book.publishedYear
            it[edition] = book.edition
        }
        if (updatedRows == 0)  null else book
    }

    override fun delete(id: Int): Boolean = transaction(database){
        BookTable.deleteWhere { BookTable.id eq id } > 0
    }
}