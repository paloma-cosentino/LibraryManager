package org.example.http

// Deserialized by http4k/Jackson via Body.auto<UpdateUserRequest>() in UserHandler —
// the IDE's "unused" inspection can't trace that reified-generic usage, so it's a false positive.
@Suppress("unused")
data class UpdateUserRequest(
    val name: String,
    val email: String
)

