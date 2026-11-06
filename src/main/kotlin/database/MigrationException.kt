package org.example.database

/** A migration refused to run because the data cannot be migrated safely; nothing was changed. */
class MigrationException(message: String) : IllegalStateException(message)
