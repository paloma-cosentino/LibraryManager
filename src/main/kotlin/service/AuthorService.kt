package org.example.service

import org.example.model.Author
import org.example.model.AuthorExceptions
import org.example.model.AuthorFilter
import org.example.model.PagedResult
import org.example.repository.AuthorRepository
import org.example.repository.BookRepository
import org.example.repository.ReferencedRowException

class AuthorService(
    private val repository: AuthorRepository,
    private val bookRepository: BookRepository
): IAuthorService {

    override fun getAllAuthors(filter: AuthorFilter): PagedResult<Author> =
        repository.findAll(filter)

    override fun getAuthorById(id: Int): Author = repository.findById(id)
        ?: throw AuthorExceptions.AuthorNotFoundException("id [$id]")

    override fun addAuthor(name: String): Author {
        if (repository.findByName(name) != null) {
            throw AuthorExceptions.DuplicateAuthorException(name)
        }
        return repository.save(Author(name = name))
    }

    override fun updateAuthor(author: Author): Author? = repository.update(author)

    // An author with books stays; the FK catches a book that races in after the check.
    override fun removeAuthor(id: Int): Boolean {
        if (bookRepository.existsByAuthorId(id)) throw AuthorExceptions.AuthorHasBooksException(id)
        return try {
            repository.delete(id)
        } catch (e: ReferencedRowException) {
            throw AuthorExceptions.AuthorHasBooksException(id)
        }
    }
}

