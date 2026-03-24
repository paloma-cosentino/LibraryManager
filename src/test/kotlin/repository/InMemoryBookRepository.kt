package org.example.repository

import org.example.model.Book

class InMemoryBookRepository : BookRepository {
    private val books = mutableMapOf<Int, Book>()
    private var nextId: Int = 1

    override fun findAll(): List<Book> = books.values.toList()
    override fun findById(id: Int): Book? = books[id]

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