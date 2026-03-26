package org.example.repository

import org.example.model.Book

interface BookRepository {
    fun findAll(): List<Book>
    fun findById(id: Int): Book?
    fun findByTitleAndAuthor(title: String, author: String): Book?
    fun save(book: Book): Book
    fun update(book: Book): Book?
    fun delete(id: Int): Boolean
}