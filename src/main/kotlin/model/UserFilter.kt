package org.example.model

data class UserFilter(
    val name: String?,
    val page: Int? = null,
    val size: Int? = null,
)
