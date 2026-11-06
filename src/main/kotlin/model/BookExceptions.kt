package org.example.model

object BookExceptions {
    class BookNotFoundException(id: Int) : IllegalArgumentException("Book with id [$id] not found")
    class DuplicateBookException(title: String, author: String) : IllegalArgumentException(
        "Book with title [$title] and [$author] already exists"
    )

    class InvalidCategoryException(value: String) : IllegalArgumentException(
        "Invalid category [$value]. Valid values: ${BookCategory.entries.map { it.displayName }}"
    )

    class InvalidQuantityException(quantity: Number) : IllegalArgumentException(
        "Invalid quantity [$quantity]. Quantity must be between 0 and ${Int.MAX_VALUE}"
    )

    class InsufficientQuantityException(title: String, quantity: Int, change: Int) : IllegalArgumentException(
        "Cannot change quantity of [$title] by [$change]: only [$quantity] in stock"
    )
}