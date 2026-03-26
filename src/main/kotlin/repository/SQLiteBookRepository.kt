package org.example.repository

import org.example.database.AuthorTable
import org.example.database.BookTable
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class SQLiteBookRepository(private val database: Database): BookRepository {
    private fun ResultRow.toBook() = Book(
        id = this[BookTable.id],
        title = this[BookTable.title],
        author = Author(
            this[AuthorTable.id],
            this[AuthorTable.name]
        ),
        category = BookCategory.entries.first { it.displayName == this[BookTable.category] },
        quantity = this[BookTable.quantity]
    )


    override fun findAll(): List<Book>  = transaction(database) {
       (BookTable innerJoin AuthorTable).selectAll().map { it.toBook() }
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
            } get BookTable.id
        )
    }

    override fun update(book: Book): Book? = transaction (database){
        val updatedRows = BookTable.update({ BookTable.id eq book.id }) {
            it[title] = book.title
            it[authorId] = book.author.id
            it[category] = book.category.displayName
            it[quantity] = book.quantity
        }
        if (updatedRows == 0)  null else book
    }

    override fun delete(id: Int): Boolean = transaction(database){
        BookTable.deleteWhere { BookTable.id eq id } > 0
    }
}