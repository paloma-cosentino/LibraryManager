package org.example.model

object AuthorizationExceptions {
    // Deliberately generic: never says whether the target exists or whom it belongs to.
    class ForbiddenException : IllegalArgumentException("Forbidden")
}
