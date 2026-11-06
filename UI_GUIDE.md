# 🖥️ UI Implementation Guide

This guide explains what has been built in the UI layer and how to follow the same pattern to add further pages.

---

## What's implemented

| File | Description |
|------|-------------|
| `src/main/kotlin/http/UiHandler.kt` | `ViewModel`s + handlers that render Handlebars templates |
| `src/main/kotlin/http/UiRoutes.kt` | UI routes (`/ui/*`) and static file serving (`/static`) — all public, see note below |
| `src/main/resources/templates/layout.hbs` | Shared base layout (doctype/head/navbar/footer) used via Handlebars **partial blocks** |
| `src/main/resources/templates/header.hbs` | Navbar partial, with active-link highlighting per page |
| `src/main/resources/templates/footer.hbs` | Footer partial — just the shared `api.js` include |
| `src/main/resources/templates/login.hbs` | Login page (doesn't use `layout.hbs`, since it has no navbar) |
| `src/main/resources/templates/books.hbs` | Books list/search; create/edit/stock/delete for ADMIN |
| `src/main/resources/templates/authors.hbs` | Authors list/search; create/edit/delete for ADMIN |
| `src/main/resources/templates/loans.hbs` | Loans list (own only for USER), borrow form with an account picker for ADMIN, return action for ADMIN |
| `src/main/resources/templates/users.hbs` | USER: own profile + "change password". ADMIN: users list/search with Role column, role toggle and delete |
| `src/main/resources/static/css/style.css` | Global styles: navbar, forms, tables, modals, pagination, badges |
| `src/main/resources/static/js/api.js` | `apiFetch` (JWT-aware fetch), `refreshCurrentUser`, `isAdmin`, `alertFailure`, `qs`, `escapeHtml`, `requireAuth`, `logout`, `currentUser` |
| `src/main/resources/static/js/*.js` | One script per page (`login.js`, `books.js`, `authors.js`, `loans.js`, `users.js`) |

| Page | Route | Template | JS | Status |
|------|-------|----------|----|--------|
| Login | `/ui/login` | `login.hbs` | `login.js` | ✅ |
| Books | `/ui/books` | `books.hbs` | `books.js` | ✅ |
| Authors | `/ui/authors` | `authors.hbs` | `authors.js` | ✅ |
| Loans | `/ui/loans` | `loans.hbs` | `loans.js` | ✅ |
| Users | `/ui/users` | `users.hbs` | `users.js` | ✅ |

---

## ⚠️ Public vs. protected routes — important lesson learned

**All `/ui/*` page routes (including books/authors/loans/users) are registered as public**, inside the same `uiRoutes(uiHandler)` call that's passed to `publicRoutes` in `Main.kt`. This is intentional, for two reasons:

1. **Browser navigation doesn't send the JWT.** The token lives in `localStorage` and is only attached manually by `apiFetch()` for `fetch()` calls. A plain `<a href="/ui/books">` click is a normal GET request with no `Authorization` header. If `/ui/books` were registered behind `authFilter`, every direct navigation without a token would get a bare `401 Unauthorized` response instead of the HTML page — so the client-side redirect-to-login logic would never even get a chance to run.
2. **The real security boundary is the REST API**, not the HTML shell. Each page's `.js` file calls `requireAuth()` on load (redirects to `/ui/login` if there's no token) and all data comes from `apiFetch()` calls to the already-protected `/books`, `/authors`, `/loans`, `/users` endpoints. Even if someone loaded the HTML directly, they'd see an empty page with no data, since every API call would 401.

### Roles in the UI

Each page awaits `refreshCurrentUser()` (`GET /auth/me`) before rendering role-dependent controls, and stores the fresh result as `localStorage.user`. The role is never taken from a stored copy, which may be stale after a promotion or demotion. Admin-only controls are `hidden` in the templates and revealed only for `role === "ADMIN"`.

Hiding is a convenience: the server enforces every permission (`403`) and guard (`409`), and pages show the server's message when an action is refused. See the permission table in the README.

**A real routing bug this uncovered:** the original `Main.kt` tried two separately-invoked routers and only treated a `404 NOT_FOUND` response as "try the other router":

```kotlin
// ❌ Don't do this — breaks as soon as a path is registered in both routers
// with different methods (e.g. POST /users public, GET /users protected).
val app = { request: Request ->
    val publicResponse = publicRoutes(request)
    if (publicResponse.status != Status.NOT_FOUND) publicResponse
    else authFilter(protectedRoutes)(request)
}
```

Because `POST /users` is public, a `GET /users` request matched that same path in the public router and got back `405 Method Not Allowed` — which is **not** `404`, so the code above returned the bare 405 and never even tried `protectedRoutes`, where the real `GET /users` handler lives. `GET /users` was completely broken until this was fixed.

**The fix** — scope the filter to just the protected group with `.withFilter(...)`, then combine everything into a **single flat `routes(...)` call** so http4k can correctly resolve 404 vs. 405 across *all* registered routes at once:

```kotlin
val protectedRoutes = routes(
    bookRoutes(bookHandler),
    authorRoutes(authorHandler),
    loanRoutes(loanHandler),
    userRoutes(userHandler)
).withFilter(authFilter(userRepository))

// Outermost: gives each request a fresh context, where authFilter stores the Actor.
val app = ServerFilters.InitialiseRequestContext(requestContexts).then(routes(
    publicUserRoutes(userHandler),
    uiRoutes(uiHandler),
    protectedRoutes
))
```

---

## How the pattern works

### Render flow

```
GET /ui/books
     │
     ▼
UiHandler.books (Kotlin)        creates BooksView(...)
     │
     ▼
HandlebarsTemplates            renders books.hbs, wrapped in layout.hbs
     │
     ▼
Response (text/html)
```

### Shared layout via partial blocks

`layout.hbs` owns the `<!DOCTYPE>`, `<head>`, navbar and footer. Every page wraps its own content in a Handlebars **partial block** so it doesn't have to repeat that boilerplate:

```handlebars
{{#> layout pageTitle="Books" isBooks=true}}
    <h2>📚 Books</h2>
    ... page content ...
{{/layout}}
```

Inside `layout.hbs`, `{{> @partial-block}}` is replaced with everything between `{{#> layout ...}}` and `{{/layout}}`. Hash arguments (`pageTitle="Books"`, `isBooks=true`) become template variables visible both in `layout.hbs` and in the `{{> header}}` partial it includes (used for active-link highlighting — see `header.hbs`).

`login.hbs` is the one exception: it doesn't use `layout.hbs` (no navbar before login), but still reuses `{{> footer}}` for the shared `api.js` script tag.

### Data flow (after the HTML loads)

```
books.hbs loads in the browser
     │
     ▼
books.js runs — requireAuth(), then await refreshCurrentUser() (GET /auth/me) for the role
     │  calls apiFetch("/books" + qs(filters))
     ▼
api.js adds "Authorization: Bearer <token>"
     │
     ▼
GET /books (REST API, behind authFilter)
     │
     ▼
JSON → renders rows into the table (via DOM APIs + escapeHtml, not raw innerHTML
       interpolation, to avoid injecting unescaped user data)
```

---

## How to add a new page (recap, using the pattern above)

1. **ViewModel + handler** in `UiHandler.kt`:
   ```kotlin
   data class BooksView(val pageTitle: String = "Books") : ViewModel {
       val isBooks = true
       override fun template() = "books"
   }
   val books: HttpHandler = { Response(Status.OK).with(viewLens of BooksView()) }
   ```
2. **Route** in `UiRoutes.kt`: add `"/ui/books" bind GET to handler.books` inside `uiRoutes(...)`.
3. **Template** `books.hbs`: wrap content in `{{#> layout pageTitle="..." isBooks=true}} ... {{/layout}}`.
4. **Script** `books.js`: start with `requireAuth();`, `await refreshCurrentUser()` before showing any role-dependent control, then use `apiFetch`/`qs`/`escapeHtml` from `api.js` to load and render data.
5. **Styles**: reuse the existing `.toolbar`, `.data-table`, `.pagination`, `.modal` classes in `style.css` rather than inventing new ones per page.

---

## Reference: `api.js`

Available globally on every page (loaded via `{{> footer}}`):

```javascript
// GET with the JWT attached automatically
apiFetch("/books" + qs({ title: "Dune", page: 1, size: 10 }))

// POST / PUT with a JSON body
apiFetch("/books", { method: "POST", body: JSON.stringify({ title: "Dune", ... }) })
apiFetch(`/books/${id}`, { method: "PUT", body: JSON.stringify({ ... }) })

// DELETE
apiFetch(`/books/${id}`, { method: "DELETE" })

// Builds "?a=1&b=2", skipping empty/undefined/null values
qs({ title: "", category: "Fantasy", page: 1 })   // → "?category=Fantasy&page=1"

// Escapes user-controlled text before interpolating into innerHTML
escapeHtml(book.title)

// Current user as last stored in localStorage, or null — may be stale; don't use it for roles
currentUser()   // → { id, name, email, role } | null

// Re-reads the signed-in account from GET /auth/me, stores it, and returns it (null on failure).
// Await this on page load before showing role-dependent controls.
await refreshCurrentUser()   // → { id, name, email, role } | null
isAdmin(user)                // → true only for role "ADMIN"

// Shows the server's message for a refused action (e.g. 403, 409)
await alertFailure(response, "Fallback message")

// Clears the token and redirects to /ui/login — also called automatically
// by apiFetch() whenever any API call responds with 401 (expired/invalid token)
logout()

// Redirects to /ui/login if there's no token saved — call this at the top
// of every page's .js file
requireAuth()
```

---

## Known limitations / follow-ups worth doing next

1. ~~**`GET /users` and `GET /users/email` leak `passwordHash`**~~ — **fixed.** Both return `UserResponse` (`id`/`name`/`email`/`role` only). `PUT /users/{id}` takes `UpdateUserRequest(name, email)` and `PATCH /users/{id}/password` (`ChangePasswordRequest(currentPassword, newPassword)`) verifies the current password before re-hashing — self-only, admins included. On the Users page the 🔑 button appears only on your own row and calls the endpoint with plain `fetch()` (not `apiFetch()`), because a wrong current password legitimately returns `401` and `apiFetch()` would force a logout. There is still no "admin resets someone else's password" feature; that is deliberate.
2. **No sign-up page** — `POST /users` (registration) is only reachable via direct API calls today; there's no `register.hbs`. New accounts are always `USER`; admins come from `ADMIN_EMAILS` or the role toggle on `/ui/users`.
3. **Loan creation uses plain `<select>`s** — all books (`/books?size=100`) and, for admins, all accounts (`/users?size=100`). Fine for a small library; a larger one would need search/pagination in the pickers.
4. **Pagination is simple Prev/Next**, no jump-to-page or page-size selector.




