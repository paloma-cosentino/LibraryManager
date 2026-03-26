package org.example.http

import org.example.model.Author
import org.example.model.Exceptions
import org.example.service.AuthorService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.int

class AuthorHandler(private val service: AuthorService) {
    private val authorLens  = Body.auto<Author>().toLens()
    private val authorsLens = Body.auto<List<Author>>().toLens()
    private val idPath      = Path.int().of("id")

    val getAll: HttpHandler = {
        authorsLens(service.getAllAuthors(), Response(OK))
    }

    val getById: HttpHandler = { request ->
        try {
            val author = service.getAuthorById(idPath(request))
            authorLens(author, Response(OK))
        } catch (e: Exceptions.AuthorNotFoundException) {
            Response(NOT_FOUND).body(e.message ?: "Author not found")
        }
    }

    val create: HttpHandler = { request ->
        try {
            val body = authorLens(request)
            val saved = service.addAuthor(name = body.name)
            authorLens(saved, Response(OK))
        } catch (e: Exceptions.DuplicateAuthorException) {
            Response(CONFLICT).body(e.message ?: "Author already exists")
        }
    }

    val update: HttpHandler = { request ->
        val updated = service.updateAuthor(authorLens(request).copy(id = idPath(request)))
        if (updated == null) Response(NOT_FOUND)
        else authorLens(updated, Response(OK))
    }

    val delete: HttpHandler = { request ->
        if (service.removeAuthor(idPath(request))) Response(NO_CONTENT)
        else Response(NOT_FOUND)
    }
}


