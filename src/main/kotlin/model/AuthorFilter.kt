package org.example.model

data class AuthorFilter(
    val name: String?,
    val page: Int? = null,
    val size: Int? = null
)
