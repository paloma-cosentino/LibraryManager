package org.example.http

import org.example.model.BookCategory
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

data class BooksView(
    val pageTitle: String = "Books",
    val categories: List<String> = BookCategory.entries.map { it.displayName }
) : ViewModel {
    // Read by header.hbs ({{#if isBooks}}) for nav active-link highlighting, not by any
    // Kotlin code, so the IDE's "unused" inspection flags these as false positives.
    @Suppress("unused")
    val isBooks = true
    override fun template() = "books"
}

data class AuthorsView(val pageTitle: String = "Authors") : ViewModel {
    @Suppress("unused")
    val isAuthors = true
    override fun template() = "authors"
}

data class LoansView(val pageTitle: String = "Loans") : ViewModel {
    @Suppress("unused")
    val isLoans = true
    override fun template() = "loans"
}

data class UsersView(val pageTitle: String = "Users") : ViewModel {
    @Suppress("unused")
    val isUsers = true
    override fun template() = "users"
}

class UiHandler {
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources/templates")
    private val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

    val login: HttpHandler = {
        Response(Status.OK).with(viewLens of LoginView())
    }

    val books: HttpHandler = {
        Response(Status.OK).with(viewLens of BooksView())
    }

    val authors: HttpHandler = {
        Response(Status.OK).with(viewLens of AuthorsView())
    }

    val loans: HttpHandler = {
        Response(Status.OK).with(viewLens of LoansView())
    }

    val users: HttpHandler = {
        Response(Status.OK).with(viewLens of UsersView())
    }
}


