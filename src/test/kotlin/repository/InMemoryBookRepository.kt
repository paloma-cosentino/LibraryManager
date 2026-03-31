package org.example.repository

import org.example.model.Book
import org.example.model.BookFilter
import org.example.model.PagedResult
import kotlin.math.ceil

class InMemoryBookRepository : BookRepository {
    private val books = mutableMapOf<Int, Book>()
    private var nextId: Int = 1

    override fun findAll(): List<Book> = books.values.toList()

    override fun findAll(filter: BookFilter): PagedResult<Book> {
        var result = books.values.toList()
        filter.title?.let    { result = result.filter { b -> b.title.contains(it, ignoreCase = true) } }
        filter.author?.let   { result = result.filter { b -> b.author.name.contains(it, ignoreCase = true) } }
        filter.category?.let { result = result.filter { b -> b.category.displayName == it } }

        val total     = result.size
        val pageNum   = filter.page ?: 1
        val pageSize  = filter.size  ?: 10
        val offset    = (pageNum - 1) * pageSize
        val paged     = result.drop(offset).take(pageSize)
        val totalPages = if (total == 0) 0 else ceil(total.toDouble() / pageSize).toInt()

        return PagedResult(data = paged, page = pageNum, pageSize = pageSize, total = total, totalPages = totalPages)
    }
    override fun findById(id: Int): Book? = books[id]
    override fun findByTitleAndAuthor(title: String, authorId: Int): Book? =
        books.values.firstOrNull { it.title == title && it.author.id == authorId }

    override fun save(book: Book): Book {
        val newBook = book.copy(id = nextId++)
        books[newBook.id] = newBook
        return newBook
    }

    override fun update(book: Book): Book? {
        if (!books.containsKey(book.id)) return null
        books[book.id] = book
        return book
    }

    override fun delete(id: Int): Boolean {
        return books.remove(id) != null
    }

}