package org.example.model

object AuthorExceptions {
    class AuthorNotFoundException(author: String) : IllegalArgumentException("Author [$author] not found")
    class DuplicateAuthorException(name: String) : IllegalArgumentException("Author [$name] already exists")
    class AuthorHasBooksException(id: Int) : IllegalArgumentException("Author [$id] has books and cannot be deleted")
}