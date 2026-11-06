package org.example.database

import org.jetbrains.exposed.sql.Table

object UserTable : Table("users")   {
    val id =integer("id").autoIncrement()
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 20).default("USER")
}