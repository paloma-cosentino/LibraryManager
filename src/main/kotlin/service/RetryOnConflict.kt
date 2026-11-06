package org.example.service

import org.example.repository.ConcurrentUpdateException

/**
 * Re-runs [block] when an optimistic-locking write loses a race. [block] must re-read current state
 * on every attempt, otherwise each retry repeats the same stale write. The final attempt's conflict
 * propagates to the caller; any other exception propagates immediately.
 */
fun <T> retryOnConflict(maxAttempts: Int = 3, block: () -> T): T {
    repeat(maxAttempts - 1) {
        try {
            return block()
        } catch (_: ConcurrentUpdateException) {
            // Lost the race; the next attempt re-reads.
        }
    }
    return block()
}
