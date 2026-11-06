package org.example.model

data class Book(
    val id: Int = 0,
    val title: String,
    val author: Author,
    val category: BookCategory,
    val quantity: Int,
    val publishedYear: Int? = null,
    val edition: String? = null,
    val version: Int = 0,
) {
    fun withQuantityChangedBy(change: Int): Book {
        // Widened so Int overflow can never wrap into a negative (or falsely valid) stock level.
        val newQuantity = quantity.toLong() + change
        if (newQuantity < 0) throw BookExceptions.InsufficientQuantityException(title, quantity, change)
        if (newQuantity > Int.MAX_VALUE) throw BookExceptions.InvalidQuantityException(newQuantity)
        return copy(quantity = newQuantity.toInt())
    }
}
