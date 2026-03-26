package org.example.service

import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Exceptions
import org.example.repository.AuthorRepository
import org.example.repository.BookRepository

class BookService(private val bookRepository: BookRepository,
    private val authorRepository: AuthorRepository) {

    fun getAllBooks(): List<Book> = bookRepository.findAll()

    fun getBookById(id: Int): Book = bookRepository.findById(id) ?: throw Exceptions.BookNotFoundException(id)

    fun addBook(title: String, authorName: String, category: String, quantity: Int): Book {
        val bookCategory = BookCategory.entries.firstOrNull { it.displayName == category }
            ?: throw Exceptions.InvalidCategoryException(category)
        val author = authorRepository.findByName(authorName)
            ?: throw Exceptions.AuthorNotFoundException(authorName)
        if (bookRepository.findByTitleAndAuthor(title, author.id) != null) {
            throw Exceptions.DuplicateBookException(title, authorName)
        }
        return bookRepository.save(Book(id = 0, title = title, author = author, category = bookCategory, quantity = quantity))
    }

    fun updateBook(book: Book): Book? = bookRepository.update(book)

    fun updateBook(id: Int, title: String, authorName: String, category: String, quantity: Int): Book? {
        val author = authorRepository.findByName(authorName)
            ?: throw Exceptions.AuthorNotFoundException(authorName)
        val bookCategory = BookCategory.entries.firstOrNull { it.displayName == category }
            ?: throw Exceptions.InvalidCategoryException(category)
        return bookRepository.update(Book(id = id, title = title, author = author, category = bookCategory, quantity = quantity))
    }

    fun removeBook(id: Int): Boolean = bookRepository.delete(id)
}
