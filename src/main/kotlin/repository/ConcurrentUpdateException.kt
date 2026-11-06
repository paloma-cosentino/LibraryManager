package org.example.repository

class ConcurrentUpdateException(entity: String, id: Int) : RuntimeException(
    "$entity with id [$id] was modified concurrently"
)
