package org.example.service

import org.example.model.Book
import org.example.model.BookFilter
import org.example.model.PagedResult

interface IBookService {
    fun getAllBooks(filter: BookFilter): PagedResult<Book>
    fun getBookById(id: Int): Book
    fun addBook(title: String, authorName: String, category: String, quantity: Int, publishedYear: Int?, edition: String?): Book
    fun updateBook(id: Int, title: String, authorName: String, category: String, quantity: Int, publishedYear: Int?, edition: String?): Book?
    fun removeBook(id: Int): Boolean
}