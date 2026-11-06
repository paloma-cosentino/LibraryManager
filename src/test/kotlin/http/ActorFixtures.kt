package org.example.http

import org.example.model.Actor
import org.example.model.Role
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters

// Ids deliberately far from the 1, 2, 3… that in-memory repositories assign, so ownership
// checks never pass by coincidence; use an Actor with a real id when testing "own" access.
val anAdmin = Actor(userId = 1001, role = Role.ADMIN)
val aUser = Actor(userId = 1002, role = Role.USER)

/** Runs this handler as [actor] without JWTs, as authFilter would after a successful DB lookup. */
fun HttpHandler.asActor(actor: Actor): HttpHandler =
    ServerFilters.InitialiseRequestContext(requestContexts)
        .then(Filter { next -> { request -> next(request.with(actorLens of actor)) } })
        .then(this)
