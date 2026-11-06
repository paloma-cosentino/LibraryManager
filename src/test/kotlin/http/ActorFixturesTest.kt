package org.example.http

import org.example.model.Actor
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import kotlin.test.*

class ActorFixturesTest {

    private var seen: Actor? = null
    private val handler: HttpHandler = { request ->
        seen = runCatching { actorLens(request) }.getOrNull()
        Response(OK)
    }

    @Test
    fun `asActor exposes the given actor to the handler`() {
        handler.asActor(aUser)(Request(GET, "/x"))
        assertEquals(aUser, seen)

        handler.asActor(anAdmin)(Request(GET, "/x"))
        assertEquals(anAdmin, seen)
    }
}
