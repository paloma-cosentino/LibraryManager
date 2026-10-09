package org.example.http

// Deserialized by http4k/Jackson via Body.auto<ChangePasswordRequest>() in UserHandler —
// the IDE's "unused" inspection can't trace that reified-generic usage, so it's a false positive.
@Suppress("unused")
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

