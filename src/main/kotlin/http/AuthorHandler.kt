package org.example.http

import org.example.model.Author
import org.example.model.AuthorFilter
import org.example.model.PagedResult
import org.example.service.IAuthorService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int

class AuthorHandler(private val service: IAuthorService){
    private val authorLens = Body.auto<Author>().toLens()
    private val idPath     = Path.int().of("id")

    private val nameQuery  = Query.optional("name")
    private val pageQuery  = Query.int().defaulted("page", 1)
    private val sizeQuery  = Query.int().defaulted("size", 10)

    private val pagedAuthorsLens = Body.auto<PagedResult<Author>>().toLens()

    val getAll: HttpHandler = { request ->
        val filter = AuthorFilter(
            name = nameQuery(request),
            page = pageQuery(request).coerceAtLeast(1),
            size = sizeQuery(request).coerceIn(1, 100)
        )
        pagedAuthorsLens(service.getAllAuthors(filter), Response(OK))
    }

    val getById: HttpHandler = { request ->
        handleAuthorExceptions {
            authorLens(service.getAuthorById(idPath(request)), Response(OK))
        }
    }

    val create: HttpHandler = { request ->
        handleAuthorExceptions {
            val saved = service.addAuthor(name = authorLens(request).name)
            authorLens(saved, Response(OK))
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


