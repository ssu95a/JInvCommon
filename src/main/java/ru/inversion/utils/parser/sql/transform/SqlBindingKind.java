package ru.inversion.utils.parser.sql.transform;

public enum SqlBindingKind {

    /**
     * Исходный JDBC-параметр ?.
     */
    JDBC_POSITIONAL,

    /**
     * Исходный именованный параметр :name.
     */
    NAMED,

    /**
     * Исходный нумерованный параметр $1, $2...
     */
    NUMBERED_POSITIONAL,

    /**
     * SQL-литерал, заменённый на ?
     */
    GENERATED_LITERAL
}