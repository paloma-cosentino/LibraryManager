package org.example.repository

import org.example.model.Book

class SQLiteBookRepository: BookRepository {
    override fun findAll(): List<Book>  {

        TODO("Not yet implemented")
    }

    override fun findById(id: Int): Book? {
        TODO("Not yet implemented")
    }

    override fun save(book: Book): Book {
        TODO("Not yet implemented")
    }

    override fun update(book: Book): Book? {
        TODO("Not yet implemented")
    }

    override fun delete(id: Int): Boolean {
        TODO("Not yet implemented")
    }
}