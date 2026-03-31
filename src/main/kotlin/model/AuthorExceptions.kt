package org.example.model

object AuthorExceptions {
    class AuthorNotFoundException(author: String) : IllegalArgumentException("Author [$author] not found")
    class DuplicateAuthorException(name: String) : IllegalArgumentException("Author [$name] already exists")
}