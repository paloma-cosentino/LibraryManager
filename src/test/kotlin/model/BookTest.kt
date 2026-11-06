package org.example.model

import kotlin.test.*

class BookTest {
    private fun bookWithQuantity(quantity: Int) =
        Book(1, "Dune", Author(1, "Frank Herbert"), BookCategory.SCIENCE_FICTION, quantity)

    @Test
    fun `withQuantityChangedBy applies a negative change`() {
        assertEquals(2, bookWithQuantity(3).withQuantityChangedBy(-1).quantity)
    }

    @Test
    fun `withQuantityChangedBy applies positive and zero changes`() {
        assertEquals(5, bookWithQuantity(3).withQuantityChangedBy(2).quantity)
        assertEquals(3, bookWithQuantity(3).withQuantityChangedBy(0).quantity)
        assertEquals(0, bookWithQuantity(1).withQuantityChangedBy(-1).quantity)
        assertEquals(Int.MAX_VALUE, bookWithQuantity(Int.MAX_VALUE - 1).withQuantityChangedBy(1).quantity)
    }

    @Test
    fun `withQuantityChangedBy throws InsufficientQuantityException below zero`() {
        assertFailsWith<BookExceptions.InsufficientQuantityException> {
            bookWithQuantity(1).withQuantityChangedBy(-2)
        }
    }

    @Test
    fun `withQuantityChangedBy rejects overflow past Int MAX_VALUE instead of wrapping negative`() {
        assertFailsWith<BookExceptions.InvalidQuantityException> {
            bookWithQuantity(Int.MAX_VALUE).withQuantityChangedBy(1)
        }
    }
}
