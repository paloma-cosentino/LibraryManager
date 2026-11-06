package org.example.service

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookFilter
import org.example.model.BookExceptions
import org.example.model.PagedResult
import org.example.repository.AuthorRepository
import org.example.repository.BookRepository

class BookService(
    private val bookRepository: BookRepository,
    private val authorRepository: AuthorRepository
): IBookService {
    override fun getAllBooks(filter: BookFilter): PagedResult<Book> =
        bookRepository.findAll(filter)

    override fun getBookById(id: Int): Book = bookRepository.findById(id)
        ?: throw BookExceptions.BookNotFoundException(id)

    override fun addBook(
        title: String,
        authorName: String,
        category: String,
        quantity: Int,
        publishedYear: Int?,
        edition: String?
    ): Book {
        if (quantity < 0) throw BookExceptions.InvalidQuantityException(quantity)
        val author = resolveAuthor(authorName)
        val bookCategory = resolveCategory(category)
        if (bookRepository.findByTitleAndAuthor(title, author.id) != null) {
            throw BookExceptions.DuplicateBookException(title, authorName)
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

    override fun updateBook(
        id: Int,
        title: String,
        authorName: String,
        category: String,
        publishedYear: Int?,
        edition: String?
    ): Book? {
        val author = resolveAuthor(authorName)
        val bookCategory = resolveCategory(category)
        return retryOnConflict {
            // Details only: quantity always comes from the freshly read row, so concurrent
            // stock changes are never overwritten by an edit.
            val current = bookRepository.findById(id) ?: return@retryOnConflict null
            bookRepository.update(
                current.copy(
                    title = title,
                    author = author,
                    category = bookCategory,
                    publishedYear = publishedYear,
                    edition = edition
                )
            )
        }
    }

    override fun adjustBookQuantity(id: Int, change: Int): Book = retryOnConflict {
        val current = getBookById(id)
        bookRepository.update(current.withQuantityChangedBy(change))
            ?: throw BookExceptions.BookNotFoundException(id)
    }

    override fun removeBook(id: Int): Boolean = bookRepository.delete(id)

    private fun resolveAuthor(authorName: String) =
        authorRepository.findByName(authorName)
            ?: authorRepository.save(Author(name = authorName))

    private fun resolveCategory(category: String) =
        BookCategory.entries.firstOrNull { it.displayName == category }
            ?: throw BookExceptions.InvalidCategoryException(category)
}
