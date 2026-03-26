package org.example.repository

import org.example.model.Author

class InMemoryAuthorRepository : AuthorRepository {
    private val authors = mutableMapOf<Int, Author>()
    private var nextId: Int = 1

    override fun findAll(): List<Author> = authors.values.toList()
    override fun findById(id: Int): Author? = authors[id]
    override fun findByName(name: String): Author? = authors.values.firstOrNull { it.name == name }

    override fun save(author: Author): Author {
        val newAuthor = author.copy(id = nextId++)
        authors[newAuthor.id] = newAuthor
        return newAuthor
    }

    override fun update(author: Author): Author? {
        if (!authors.containsKey(author.id)) return null
        authors[author.id] = author
        return author
    }

    override fun delete(id: Int): Boolean = authors.remove(id) != null
}

