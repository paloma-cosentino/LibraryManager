package org.example.repository

import org.example.database.UserTable
import org.example.database.UserTable.email
import org.example.database.UserTable.name
import org.example.model.PagedResult
import org.example.model.Role
import org.example.model.User
import org.example.model.UserFilter
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.ceil

class SQLiteUserRepository(private val database: Database) : UserRepository {
    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        name = this[name],
        email = this[email],
        passwordHash = this[UserTable.passwordHash],
        role = Role.valueOf(this[UserTable.role])
    )
    override fun findAll(filter: UserFilter): PagedResult<User> = transaction(database) {
        var query =UserTable.selectAll()

       filter.name?.let { query = query.andWhere { name like "%$it%" } }

        val total = query.count().toInt()
        val pageNum = filter.page ?: 1
        val pageSize = filter.size ?: 10
        val offset = ((pageNum - 1) * pageSize).toLong()
        val users = query.limit(pageSize).offset(offset).map { it.toUser() }
        val totalPages = if (total == 0) 0 else ceil(total.toDouble() / pageSize).toInt()

        PagedResult(
            data = users,
            page = pageNum,
            pageSize = pageSize,
            total = total,
            totalPages = totalPages
        )
    }
    override fun findById(id: Int): User? = transaction(database) {
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .map { it.toUser() }
            .firstOrNull()
    }
    override fun findByEmail(email: String): User? = transaction(database) {
        UserTable.selectAll()
            .where { UserTable.email eq email }
            .map { it.toUser() }
            .firstOrNull()

    }
    override fun save(user: User): User = transaction(database) {
        user.copy(
            id = UserTable.insert {
                it[name] = user.name
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[UserTable.role] = user.role.name
            } get UserTable.id
        )
    }
    // Role is deliberately not written here: profile/password edits carry a possibly stale role,
    // and writing it back would undo a concurrent demotion. Role changes go through updateRole only.
    override fun update(user: User): User? = transaction(database) {
        val updatedRow = UserTable.update({ UserTable.id eq user.id }) {
            it[name] = user.name
            it[email] = user.email
            it[passwordHash] = user.passwordHash
        }
        if (updatedRow == 0) null else findById(user.id)
    }
    override fun updateRole(id: Int, role: Role): Boolean = transaction(database) {
        UserTable.update({ UserTable.id eq id }) { it[UserTable.role] = role.name } > 0
    }

    override fun countByRole(role: Role): Int = transaction(database) {
        UserTable.selectAll().where { UserTable.role eq role.name }.count().toInt()
    }

    override fun delete(id: Int): Boolean = translatingForeignKeyViolations("User", id) {
        transaction(database) { UserTable.deleteWhere { UserTable.id eq id } > 0 }
    }
}