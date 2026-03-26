package org.example.model

import com.fasterxml.jackson.annotation.JsonValue

enum class BookCategory(@JsonValue val displayName: String) {
    FICTION("Fiction"),
    NON_FICTION("Non-Fiction"),
    SCIENCE_FICTION("Science Fiction"),
    FANTASY("Fantasy"),
    HORROR("Horror"),
    MYSTERY("Mystery"),
    BIOGRAPHY("Biography"),
    HISTORY("History"),
    SCIENCE("Science"),
    TECHNOLOGY("Technology")
}