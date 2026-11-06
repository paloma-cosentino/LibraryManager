package org.example.http

import com.auth0.jwt.exceptions.JWTVerificationException
import org.example.model.Actor
import org.example.repository.UserRepository
import org.example.security.JwtService
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.RequestContexts
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.RequestContextKey

// One store for the whole app; ServerFilters.InitialiseRequestContext(requestContexts) must wrap it.
val requestContexts = RequestContexts()
val actorLens = RequestContextKey.required<Actor>(requestContexts)

// Identity comes from the verified token; the role always comes from the database, re-read on every
// request, so promotions/demotions/deletions apply immediately rather than at token expiry.
fun authFilter(users: UserRepository): Filter = Filter { next ->
    { request ->
        val actor = bearerToken(request)?.let(::verifiedUserId)?.let(users::findById)
            ?.let { Actor(it.id, it.role) }
        if (actor == null) Response(Status.UNAUTHORIZED)
        else next(request.with(actorLens of actor))
    }
}

private fun bearerToken(request: Request): String? =
    request.header("Authorization")?.takeIf { it.startsWith(BEARER) }?.removePrefix(BEARER)

private fun verifiedUserId(token: String): Int? =
    try {
        JwtService.verifyToken(token).getClaim("userId").asInt()
    } catch (_: JWTVerificationException) {
        null
    }

private const val BEARER = "Bearer "

// Must sit inside authFilter: it relies on the DB-sourced Actor being in the request context.
val requireAdmin: Filter = Filter { next ->
    { request -> if (actorLens(request).isAdmin) next(request) else Response(Status.FORBIDDEN) }
}
