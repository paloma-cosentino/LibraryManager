package org.example.service

import org.example.model.Author
import org.example.model.AuthorFilter
import org.example.model.Exceptions
import org.example.model.PagedResult
import org.example.repository.AuthorRepository

class AuthorService(private val repository: AuthorRepository) {

    fun getAllAuthors(filter: AuthorFilter): PagedResult<Author> =
        repository.findAll(filter)

    fun getAuthorById(id: Int): Author = repository.findById(id)
        ?: throw Exceptions.AuthorNotFoundException("id [$id]")

    fun addAuthor(name: String): Author {
        if (repository.findByName(name) != null) {
            throw Exceptions.DuplicateAuthorException(name)
        }
        return repository.save(Author(name = name))
    }

    fun updateAuthor(author: Author): Author? = repository.update(author)

    fun removeAuthor(id: Int): Boolean = repository.delete(id)
}

