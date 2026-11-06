package org.example.repository

import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

/** A write was rejected by the database because it would break a foreign-key reference. */
class ReferencedRowException(entity: String, id: Int, cause: Throwable) :
    RuntimeException("$entity [$id] would break a reference between rows", cause)

/**
 * Turns SQLite foreign-key violations (possibly wrapped by Exposed) into [ReferencedRowException];
 * every other failure propagates unchanged.
 */
internal inline fun <T> translatingForeignKeyViolations(entity: String, id: Int, write: () -> T): T =
    try {
        write()
    } catch (e: Exception) {
        if (e.isForeignKeyViolation()) throw ReferencedRowException(entity, id, e) else throw e
    }

// Inserts violating a reference report SQLITE_CONSTRAINT_FOREIGNKEY, but deletes blocked by an
// ON DELETE RESTRICT action report SQLITE_CONSTRAINT_TRIGGER with SQLite's fixed FK message
// (observed with sqlite-jdbc 3.47.1); a trigger code alone is not enough to mean a foreign key.
@PublishedApi
internal fun Throwable.isForeignKeyViolation(): Boolean =
    generateSequence(this) { it.cause }.filterIsInstance<SQLiteException>().any {
        it.resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY ||
            (it.resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER && FOREIGN_KEY_MESSAGE in (it.message ?: ""))
    }

@PublishedApi
internal const val FOREIGN_KEY_MESSAGE = "FOREIGN KEY constraint failed"
