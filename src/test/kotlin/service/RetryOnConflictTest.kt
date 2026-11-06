package org.example.service

import org.example.repository.ConcurrentUpdateException
import kotlin.test.*

class RetryOnConflictTest {

    @Test
    fun `returns the result of the block`() {
        assertEquals("done", retryOnConflict { "done" })
    }

    @Test
    fun `retries after a conflict and returns the later result`() {
        var attempts = 0

        val result = retryOnConflict {
            attempts++
            if (attempts == 1) throw ConcurrentUpdateException("Book", 1)
            "done"
        }

        assertEquals("done", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `rethrows the conflict after three attempts`() {
        var attempts = 0

        assertFailsWith<ConcurrentUpdateException> {
            retryOnConflict<Unit> {
                attempts++
                throw ConcurrentUpdateException("Book", 1)
            }
        }
        assertEquals(3, attempts)
    }

    @Test
    fun `does not retry other exceptions`() {
        var attempts = 0

        assertFailsWith<IllegalStateException> {
            retryOnConflict<Unit> {
                attempts++
                throw IllegalStateException("not a conflict")
            }
        }
        assertEquals(1, attempts)
    }
}
