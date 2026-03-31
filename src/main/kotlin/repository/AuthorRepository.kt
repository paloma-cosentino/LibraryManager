package org.example.repository

import org.example.model.Author
import org.example.model.AuthorFilter
import org.example.model.PagedResult

interface AuthorRepository {
    fun findAll(filter: AuthorFilter): PagedResult<Author>
    fun findById(id: Int): Author?
    fun findByName(name: String): Author?
    fun save(author: Author): Author
    fun update(author: Author): Author?
    fun delete(id: Int): Boolean
}
