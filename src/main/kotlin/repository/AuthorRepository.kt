package org.example.repository

import org.example.model.Author

interface AuthorRepository {
    fun findAll(): List<Author>
    fun findById(id: Int): Author?
    fun findByName(name: String): Author?
    fun save(author: Author): Author
    fun update(author: Author): Author?
    fun delete(id: Int): Boolean
}
