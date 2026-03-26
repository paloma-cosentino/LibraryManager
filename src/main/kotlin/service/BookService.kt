package org.example.service

import org.example.model.Book
import org.example.model.Exceptions
import org.example.repository.BookRepository

class BookService(private val repository: BookRepository) {

    fun getAllBooks(): List<Book> = repository.findAll()

    fun getBookById(id: Int): Book? = repository.findById(id)

    fun addBook(title: String, author: String, category: String, quantity: Int): Book {
        val book = Book(id = 0, title = title, author = author, category = category, quantity = quantity)
        if( repository.findByTitleAndAuthor(title, author) != null ) {
            throw Exceptions.DuplicateBookException(title, author)
        }
        return repository.save(book)
    }

    fun updateBook(book: Book): Book? = repository.update(book)

    fun removeBook(id: Int): Boolean = repository.delete(id)
}
