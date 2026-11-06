package org.example.model

data class User(
    val id: Int = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val role: Role = Role.USER
)
