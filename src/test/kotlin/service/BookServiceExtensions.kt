package org.example.service

import org.example.model.Book

fun IBookService.addBook(
    title: String,
    authorName: String,
    category: String,
    quantity: Int
): Book = addBook(title, authorName, category, quantity, publishedYear = null, edition = null)

fun IBookService.updateBook(
    id: Int,
    title: String,
    authorName: String,
    category: String,
    quantity: Int
): Book? = updateBook(id, title, authorName, category, quantity, publishedYear = null, edition = null)

