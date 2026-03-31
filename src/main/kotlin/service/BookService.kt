package org.example.service

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookFilter
import org.example.model.Exceptions
import org.example.model.PagedResult
import org.example.repository.AuthorRepository
import org.example.repository.BookRepository

class BookService(
    private val bookRepository: BookRepository,
    private val authorRepository: AuthorRepository
) {
    fun getAllBooks(filter: BookFilter): PagedResult<Book> =
        bookRepository.findAll(filter)

    fun getBookById(id: Int): Book = bookRepository.findById(id)
        ?: throw Exceptions.BookNotFoundException(id)

    fun addBook(
        title: String,
        authorName: String,
        category: String,
        quantity: Int,
        publishedYear: Int? = null,
        edition: String? = null
    ): Book {
        val author = resolveAuthor(authorName)
        val bookCategory = resolveCategory(category)
        if (bookRepository.findByTitleAndAuthor(title, author.id) != null) {
            throw Exceptions.DuplicateBookException(title, authorName)
        }
        return bookRepository.save(
            Book(
                id = 0,
                title = title,
                author = author,
                category = bookCategory,
                quantity = quantity,
                publishedYear = publishedYear,
                edition = edition
            )
        )
    }

    fun updateBook(
        id: Int,
        title: String,
        authorName: String,
        category: String,
        quantity: Int,
        publishedYear: Int? = null,
        edition: String? = null
    ): Book? {
        val author = resolveAuthor(authorName)
        val bookCategory = resolveCategory(category)
        return bookRepository.update(
            Book(
                id = id,
                title = title,
                author = author,
                category = bookCategory,
                quantity = quantity,
                publishedYear = publishedYear,
                edition = edition
            )
        )
    }

    fun removeBook(id: Int): Boolean = bookRepository.delete(id)

    private fun resolveAuthor(authorName: String) =
        authorRepository.findByName(authorName)
            ?: authorRepository.save(Author(name = authorName))

    private fun resolveCategory(category: String) =
        BookCategory.entries.firstOrNull { it.displayName == category }
            ?: throw Exceptions.InvalidCategoryException(category)
}
