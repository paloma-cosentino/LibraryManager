package org.example.model

object Exceptions{
    //class BookNotFoundException(id: Int) : IllegalArgumentException("Book with id [$id] not found")
    class DuplicateBookException(title: String, author: String) : IllegalArgumentException("Book with title [$title] and [$author] already exists")

}