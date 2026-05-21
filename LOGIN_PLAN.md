# 🔐 Plano de Implementação de Login de Usuários

## Visão Geral

Implementação de autenticação de usuários no projeto **LibraryManager**, utilizando a stack atual (http4k + Exposed + SQLite), com hash de senha via **BCrypt** e tokens de sessão via **JWT**.

---

## 1. Dependências (`build.gradle.kts`)

Adicionar as seguintes dependências:

```kotlin
// Hash de senha
implementation("org.mindrot:jbcrypt:0.4")

// JWT para tokens de sessão
implementation("com.auth0:java-jwt:4.4.0")
```

---

## 2. Banco de Dados (`UserTable.kt`)

Adicionar a coluna `passwordHash` na tabela de usuários:

```kotlin
val passwordHash = varchar("password_hash", 255)
```

> A coluna `email` já existe com `uniqueIndex()`, o que é ideal para login.

> ⚠️ Será necessário recriar ou migrar o banco `library.db` após a alteração.

---

## 3. Models

Separar os modelos para não expor o hash de senha nas respostas:

| Classe | Descrição |
|--------|-----------|
| `User` | Modelo interno (inclui `passwordHash`) |
| `UserResponse` | DTO de resposta (sem `passwordHash`) |
| `LoginRequest` | DTO de entrada para login |
| `LoginResponse` | DTO de resposta com token e dados do usuário |

Exemplo:

```kotlin
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val user: UserResponse)
```

---

## 4. JwtService (novo arquivo `src/main/kotlin/security/JwtService.kt`)

Responsável por gerar e validar tokens JWT:

```kotlin
object JwtService {
    fun generateToken(userId: Int, email: String): String { ... }
    fun verifyToken(token: String): DecodedJWT { ... }
    fun isValid(token: String): Boolean { ... }
}
```

---

## 5. UserService (`IUserService` + `UserService.kt`)

Adicionar os seguintes métodos:

```kotlin
fun registerUser(name: String, email: String, password: String): UserResponse
fun login(email: String, password: String): LoginResponse
```

- **`registerUser`**: valida dados, faz hash da senha com BCrypt e salva no banco:
  ```kotlin
  BCrypt.hashpw(password, BCrypt.gensalt())
  ```
- **`login`**: busca usuário por email, valida a senha com BCrypt e gera um JWT:
  ```kotlin
  BCrypt.checkpw(password, user.passwordHash)
  ```

---

## 6. HTTP — Handlers e Rotas

### Novos endpoints em `UserHandler.kt`:

```kotlin
val login: HttpHandler = { request ->
    val body = loginRequestLens(request)
    handleUserExceptions {
        val response = service.login(body.email, body.password)
        loginResponseLens(response, Response(Status.OK))
    }
}

val register: HttpHandler = { request ->
    val body = registerRequestLens(request)
    handleUserExceptions {
        val response = service.registerUser(body.name, body.email, body.password)
        userResponseLens(response, Response(Status.CREATED))
    }
}
```

### Novas rotas públicas:

```
POST /auth/register  →  Cria usuário com senha
POST /auth/login     →  Retorna JWT + dados do usuário
```

---

## 7. Middleware de Autenticação (novo `src/main/kotlin/http/AuthFilter.kt`)

Filtro http4k que intercepta rotas protegidas e valida o JWT no header:

```kotlin
fun authFilter(next: HttpHandler): HttpHandler = { request ->
    val token = request.header("Authorization")?.removePrefix("Bearer ")
    if (token == null || !JwtService.isValid(token)) {
        Response(Status.UNAUTHORIZED)
    } else {
        next(request)
    }
}
```

### Aplicar em `Main.kt`:

```kotlin
routes(
    authRoutes(userHandler),                   // rotas públicas
    authFilter(bookRoutes(bookHandler)),        // rotas protegidas
    authFilter(loanRoutes(loanHandler)),        // rotas protegidas
    authFilter(authorRoutes(authorHandler)),    // rotas protegidas
)
```

---

## 8. Testes

| Arquivo | O que testar |
|---------|-------------|
| `service/UserServiceTest.kt` | `login()` com credenciais válidas e inválidas; `registerUser()` com email duplicado |
| `http/UserHandlerTest.kt` | `POST /auth/login` — sucesso, senha errada, usuário inexistente |
| `http/BookHandlerTest.kt` (atualizar) | Garantir que rotas protegidas retornam `401` sem token |

---

## 📋 Ordem de Implementação

| Passo | Tarefa | Arquivos Envolvidos |
|-------|--------|---------------------|
| 1 | Adicionar dependências | `build.gradle.kts` |
| 2 | Alterar tabela de usuários | `UserTable.kt` |
| 3 | Criar DTOs de login | `model/LoginRequest.kt`, `model/LoginResponse.kt`, `model/UserResponse.kt` |
| 4 | Criar `JwtService` | `security/JwtService.kt` |
| 5 | Atualizar `IUserService` e `UserService` | `service/UserService.kt` |
| 6 | Adicionar handlers e rotas `/auth/*` | `http/UserHandler.kt`, `http/UserRoutes.kt` |
| 7 | Criar `AuthFilter` e aplicar nas rotas | `http/AuthFilter.kt`, `Main.kt` |
| 8 | Atualizar e criar testes | `test/http/`, `test/service/` |

---

## 🔒 Fluxo de Autenticação

```
Cliente                     Servidor
  │                             │
  │  POST /auth/register        │
  │ ─────────────────────────► │  Salva usuário com BCrypt hash
  │ ◄───────────── 201 Created  │
  │                             │
  │  POST /auth/login           │
  │ ─────────────────────────► │  Valida senha → gera JWT
  │ ◄──────────── 200 + token   │
  │                             │
  │  GET /books (Bearer token)  │
  │ ─────────────────────────► │  AuthFilter valida JWT
  │ ◄─────────────── 200 OK     │
  │                             │
  │  GET /books (sem token)     │
  │ ─────────────────────────► │  AuthFilter rejeita
  │ ◄────────────── 401 Unauth  │
```

