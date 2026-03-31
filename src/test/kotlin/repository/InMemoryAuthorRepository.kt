package org.example.repository

import org.example.model.Author
import org.example.model.AuthorFilter
import org.example.model.PagedResult
import kotlin.math.ceil

class InMemoryAuthorRepository : AuthorRepository {
    private val authors = mutableMapOf<Int, Author>()
    private var nextId: Int = 1


    override fun findAll(filter: AuthorFilter): PagedResult<Author> {
        var result = authors.values.toList()
        filter.name?.let { result = result.filter { a -> a.name.contains(it, ignoreCase = true) } }

        val total      = result.size
        val pageNum    = filter.page ?: 1
        val pageSize   = filter.size ?: 10
        val offset     = (pageNum - 1) * pageSize
        val paged      = result.drop(offset).take(pageSize)
        val totalPages = if (total == 0) 0 else ceil(total.toDouble() / pageSize).toInt()

        return PagedResult(data = paged, page = pageNum, pageSize = pageSize, total = total, totalPages = totalPages)
    }
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

