# Arquitetura do LibraryManager

## Visão Geral

O projeto é uma **API REST** para controle de estoque de uma biblioteca, construída em **Kotlin** com **http4k** e persistência em **SQLite**.

Segue uma **arquitetura em camadas**, onde cada camada tem uma responsabilidade única e só se comunica com a camada imediatamente abaixo dela:

```
HTTP Request
     ↓
┌─────────────┐
│   Routes    │  define URLs e métodos HTTP
├─────────────┤
│   Handler   │  lê/escreve HTTP (Lenses), delega ao serviço
├─────────────┤
│   Service   │  regras de negócio
├─────────────┤
│ Repository  │  acesso a dados (interface + implementação)
├─────────────┤
│  SQLite DB  │  persistência
└─────────────┘
```

---

## Estrutura de Pastas

```
src/
├── main/kotlin/
│   ├── Main.kt               ← ponto de entrada, composição manual de dependências
│   ├── model/                ← domínio: entidades, filtros, exceções, enums
│   ├── database/             ← schema das tabelas (Exposed) e fábrica de conexão
│   ├── repository/           ← interfaces e implementações SQLite de acesso a dados
│   ├── service/              ← interfaces e implementações de regras de negócio
│   └── http/                 ← handlers, rotas e utilitários HTTP
│
└── test/kotlin/
    ├── http/                 ← testes de handler (usam repositórios em memória)
    ├── repository/           ← testes de repositório SQLite + implementações in-memory
    └── service/              ← testes de serviço
```

---

## Camadas em Detalhe

### `model/` — Domínio

É o coração do projeto. Não depende de nenhuma outra camada.

| Arquivo | Descrição |
|---|---|
| `Book.kt` | Data class da entidade livro |
| `Author.kt` | Data class da entidade autor |
| `Loan.kt` | Data class do empréstimo; contém propriedades computadas `isReturned` e `isOverdue` |
| `BookCategory.kt` | Enum com as categorias válidas de livro e seus `displayName` |
| `BookFilter.kt` | Parâmetros de busca e paginação para livros |
| `AuthorFilter.kt` | Parâmetros de busca e paginação para autores |
| `LoanFilter.kt` | Parâmetros de busca e paginação para empréstimos (inclui `onlyActive`) |
| `PagedResult.kt` | Envelope genérico `PagedResult<T>` para respostas paginadas |
| `BookExceptions.kt` | Exceções tipadas de negócio para livros (Not Found, Duplicate, Invalid Category) |
| `AuthorExceptions.kt` | Exceções tipadas de negócio para autores (Not Found, Duplicate) |
| `LoanExceptions.kt` | Exceções tipadas de negócio para empréstimos (Not Found, Already Returned, Book Not Available) |

---

### `database/` — Schema e Conexão

Define a estrutura das tabelas usando o DSL do **Exposed** e inicializa o banco.

| Arquivo | Descrição |
|---|---|
| `DatabaseFactory.kt` | Singleton que conecta ao `library.db` e cria as tabelas se não existirem |
| `BookTable.kt` | Schema da tabela `books` |
| `AuthorTable.kt` | Schema da tabela `authors` |
| `LoanTable.kt` | Schema da tabela `loans`; possui FK para `books` |

> **Nota:** Nenhuma lógica de negócio aqui — apenas estrutura de dados.

---

### `repository/` — Acesso a Dados

Cada entidade tem uma **interface** e uma **implementação SQLite**. A separação por interface permite trocar a implementação (ex.: usar banco em memória nos testes) sem alterar nenhuma outra camada.

| Arquivo | Descrição |
|---|---|
| `BookRepository.kt` | Interface: `findAll`, `findById`, `findByTitleAndAuthor`, `save`, `update`, `delete` |
| `SQLiteBookRepository.kt` | Implementação com Exposed; converte `ResultRow` → `Book` |
| `AuthorRepository.kt` | Interface: `findAll`, `findById`, `findByName`, `save`, `update`, `delete` |
| `SQLiteAuthorRepository.kt` | Implementação com Exposed |
| `LoanRepository.kt` | Interface: `findAll`, `findById`, `save`, `update` |
| `SQLiteLoanRepository.kt` | Implementação com Exposed; faz JOIN entre `loans`, `books` e `authors` |

---

### `service/` — Regras de Negócio

Orquestra as operações, aplica validações e lança exceções de domínio. Recebe repositórios via construtor (**injeção de dependência**).

| Arquivo | Descrição |
|---|---|
| `IBookService.kt` | Interface do serviço de livros |
| `BookService.kt` | Valida categoria, impede duplicatas, delega CRUD ao repositório |
| `IAuthorService.kt` | Interface do serviço de autores |
| `AuthorService.kt` | Impede autor duplicado, delega CRUD ao repositório |
| `ILoanService.kt` | Interface do serviço de empréstimos |
| `LoanService.kt` | Verifica disponibilidade do livro, decrementa/incrementa estoque, registra devolução |

---

### `http/` — Camada HTTP

Faz a ponte entre o protocolo HTTP e o domínio da aplicação.

| Arquivo | Descrição |
|---|---|
| `BookRoutes.kt` | Mapeia verbos HTTP e URLs para handlers: `GET/POST /books`, `GET/PUT/DELETE /books/{id}` |
| `BookHandler.kt` | Usa **Lenses** para extrair parâmetros/body; chama o serviço; monta a `Response` |
| `AuthorRoutes.kt` | Mapeia rotas de autores |
| `AuthorHandler.kt` | Handler de autores |
| `LoanRoutes.kt` | Mapeia `GET/POST /loans`, `GET /loans/{id}`, `PATCH /loans/{id}/return` |
| `LoanHandler.kt` | Handler de empréstimos |
| `CreateBookRequest.kt` | Data class com os campos esperados no body de criação/atualização de livro |
| `LoanRequest.kt` | Data class com os campos do body de criação de empréstimo (`bookId`, `borrowerName`, `daysToReturn`) |
| `HandlerUtils.kt` | Funções `handleBookExceptions`, `handleAuthorExceptions`, `handleLoanExceptions` — mapeiam exceções de domínio para status HTTP corretos (404, 409, 400) |

#### O que é uma Lens?

Uma **Lens** é um objeto que sabe como **ler** ou **escrever** um valor tipado de/para uma estrutura HTTP. Elimina parsing manual de JSON, query strings e path variables:

```kotlin
// Lê o {id} da URL como Int
val idPath = Path.int().of("id")
val id = idPath(request)               // → Int

// Desserializa o body da requisição como LoanRequest
val loanRequestLens = Body.auto<LoanRequest>().toLens()
val body = loanRequestLens(request)    // → LoanRequest

// Serializa um Loan no body da resposta como JSON
val loanLens = Body.auto<Loan>().toLens()
loanLens(loan, Response(OK))           // → Response com JSON
```

---

### `Main.kt` — Composição da Aplicação

Ponto de entrada. Instancia todas as dependências manualmente (sem framework de DI) e registra as rotas no servidor Jetty:

```
DatabaseFactory.init()
  ├── SQLiteAuthorRepository → AuthorService → AuthorHandler → authorRoutes()  ─┐
  ├── SQLiteBookRepository   → BookService   → BookHandler   → bookRoutes()    ─┼─► Jetty(8080)
  └── SQLiteLoanRepository   → LoanService   → LoanHandler   → loanRoutes()   ─┘
```

---

## Estratégia de Testes

| Tipo de teste | Localização | Estratégia |
|---|---|---|
| Handler (HTTP) | `test/kotlin/http/` | Repositórios **em memória** — sem I/O, execução rápida |
| Repositório | `test/kotlin/repository/` | SQLite em **arquivo temporário** — valida SQL real |
| Serviço | `test/kotlin/service/` | Repositórios em memória — isola regras de negócio |

Os repositórios em memória (`InMemoryBookRepository`, `InMemoryLoanRepository`, etc.) vivem em `src/test/` e implementam as mesmas interfaces dos repositórios SQLite, garantindo que os testes de handler exercitem o mesmo contrato que a produção.

---

## Tecnologias

| Tecnologia | Versão | Papel |
|---|---|---|
| Kotlin | 2.x | Linguagem principal |
| http4k | 5.x | Framework HTTP funcional (server + client + lenses) |
| Jetty | embutido | Servidor HTTP |
| Exposed | 0.5x | ORM/DSL para SQLite |
| SQLite | — | Banco de dados embarcado |
| Jackson | — | Serialização JSON (via http4k-format-jackson) |
| Kotlin Test | — | Framework de testes (JUnit 5 por baixo) |

