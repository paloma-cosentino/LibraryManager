package org.example.service

import org.example.model.Author
import org.example.model.AuthorFilter
import org.example.model.PagedResult

interface IAuthorService {
    fun getAllAuthors(filter: AuthorFilter): PagedResult<Author>
    fun getAuthorById(id: Int): Author
    fun addAuthor(name: String): Author
    fun updateAuthor(author: Author): Author?
    fun removeAuthor(id: Int): Boolean
}