package ru.inversion.utils.parser.sql.dialect;

/**
 * Диалектные синтаксические возможности,
 * которые могут потребоваться SQL-препроцессору.
 */
public enum SqlSyntaxFeature {

    /**
     * PostgreSQL dollar-quoted string:
     *
     * <pre>
     * $$text$$
     * $tag$text$tag$
     * </pre>
     */
    POSTGRES_DOLLAR_QUOTED_STRING,

    /**
     * Oracle alternative quoted string:
     *
     * <pre>
     * q'[text]'
     * q'{text}'
     * q'(text)'
     * q'&lt;text&gt;'
     * </pre>
     */
    ORACLE_Q_QUOTED_STRING
}