package org.example.repository

import org.example.model.Book
import org.example.model.BookFilter
import org.example.model.PagedResult

interface BookRepository {
    fun findAll(filter: BookFilter): PagedResult<Book>
    fun findById(id: Int): Book?
    fun findByTitleAndAuthor(title: String, authorId: Int): Book?
    fun save(book: Book): Book
    fun update(book: Book): Book?
    fun delete(id: Int): Boolean
}