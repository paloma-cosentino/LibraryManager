package org.example.repository

import org.example.model.PagedResult
import org.example.model.User
import org.example.model.UserFilter
import kotlin.math.ceil

class InMemoryUserRepository : UserRepository {
    private val users  = mutableMapOf<Int, User>()
    private var nextId = 1

    override fun findAll(filter: UserFilter): PagedResult<User> {
        var result = users.values.toList()
        filter.name?.let { result = result.filter { u -> u.name.contains(it, ignoreCase = true) } }

        val total      = result.size
        val pageNum    = filter.page ?: 1
        val pageSize   = filter.size ?: 10
        val offset     = (pageNum - 1) * pageSize
        val paged      = result.drop(offset).take(pageSize)
        val totalPages = if (total == 0) 0 else ceil(total.toDouble() / pageSize).toInt()

        return PagedResult(data = paged, page = pageNum, pageSize = pageSize, total = total, totalPages = totalPages)
    }

    override fun findById(id: Int): User? = users[id]

    override fun findByEmail(email: String): User? = users.values.firstOrNull { it.email == email }

    override fun save(user: User): User {
        val saved = user.copy(id = nextId++)
        users[saved.id] = saved
        return saved
    }

    override fun update(user: User): User? {
        if (!users.containsKey(user.id)) return null
        users[user.id] = user
        return user
    }

    override fun delete(id: Int): Boolean = users.remove(id) != null
}

