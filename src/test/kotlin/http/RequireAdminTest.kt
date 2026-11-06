package org.example.http

import org.example.model.Actor
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import kotlin.test.*

class RequireAdminTest {

    private var downstreamCalls = 0
    private val downstream: HttpHandler = { downstreamCalls++; Response(OK) }

    private fun runAs(actor: Actor) = requireAdmin.then(downstream).asActor(actor)(Request(GET, "/x"))

    @Test
    fun `USER is forbidden and downstream does not run`() {
        assertEquals(FORBIDDEN, runAs(aUser).status)
        assertEquals(0, downstreamCalls)
    }

    @Test
    fun `ADMIN passes through`() {
        assertEquals(OK, runAs(anAdmin).status)
        assertEquals(1, downstreamCalls)
    }
}
