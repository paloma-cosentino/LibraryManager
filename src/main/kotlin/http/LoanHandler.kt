package org.example.http

import org.example.model.Loan
import org.example.model.LoanFilter
import org.example.model.PagedResult
import org.example.service.ILoanService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.boolean
import org.http4k.lens.int
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.OK


class LoanHandler(private val service: ILoanService) {
    private val loanLens        = Body.auto<Loan>().toLens()
    private val loanRequestLens = Body.auto<LoanRequest>().toLens()
    private val pagedLoansLens  = Body.auto<PagedResult<Loan>>().toLens()
    private val idPath          = Path.int().of("id")

    private val borrowerQuery   = Query.optional("borrower")
    private val bookIdQuery     = Query.int().optional("bookId")
    private val onlyActiveQuery = Query.boolean().defaulted("onlyActive", false)
    private val pageQuery       = Query.int().defaulted("page", 1)
    private val sizeQuery       = Query.int().defaulted("size", 10)

    val getAll: HttpHandler = { request: Request ->
        val filter = LoanFilter(
            borrowerName = borrowerQuery(request),
            bookId = bookIdQuery(request),
            onlyActive = onlyActiveQuery(request),
            page = pageQuery(request),
            size = sizeQuery(request)
        )
        pagedLoansLens(service.getAllLoans(filter), Response(OK))
    }

    val getById: HttpHandler = { request: Request ->
        handleLoanExceptions {
            loanLens(service.getLoanById(idPath(request)), Response(OK))
        }
    }

    val borrow: HttpHandler = { request: Request ->
        handleLoanExceptions {
            val body = loanRequestLens(request)
            loanLens(service.borrowBook(body.bookId, body.borrowerName, body.daysToReturn), Response(CREATED))
        }
    }

    val returnBook: HttpHandler = { request: Request ->
        handleLoanExceptions {
            loanLens(service.returnBook(idPath(request)), Response(OK))
        }
    }
}