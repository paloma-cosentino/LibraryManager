package org.example.model

data class PagedResult<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int
)
