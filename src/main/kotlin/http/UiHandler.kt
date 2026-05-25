package org.example.http

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel

data class LoginView(val error: String? = null) : ViewModel {
    override fun template() = "login"
}

class UiHandler {
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources/templates")
    private val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

    val login: HttpHandler = {
        Response(Status.OK).with(viewLens of LoginView())
    }
}

