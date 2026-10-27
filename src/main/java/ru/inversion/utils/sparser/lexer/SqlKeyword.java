package ru.inversion.utils.sparser.lexer;

/**
 * SQL words that have structural meaning for sparser.
 *
 * This is intentionally not a complete Oracle/PostgreSQL keyword catalog.
 * WORD remains the lexical token kind; SqlKeyword is the parser-level
 * classification used by the structural SQL code.
 */
public enum SqlKeyword {
    AND,
    AS,
    ASC,
    BIT,
    BY,
    CHARACTER,
    COLLATE,
    CONNECT,
    CURRENT_TIME,
    CURRENT_TIMESTAMP,
    DATE,
    DAY,
    DESC,
    DISTINCT,
    DOUBLE,
    EXCEPT,
    FETCH,
    FOR,
    FROM,
    GROUP,
    HAVING,
    HOUR,
    INTERSECT,
    INTERVAL,
    KEY,
    LIMIT,
    LOCALTIME,
    LOCALTIMESTAMP,
    MINUS,
    MINUTE,
    MODEL,
    MONTH,
    NO,
    NULLS,
    OFFSET,
    OR,
    ORDER,
    PRECISION,
    QUALIFY,
    SECOND,
    SELECT,
    SHARE,
    SIBLINGS,
    START,
    TIME,
    TIMESTAMP,
    UNION,
    UPDATE,
    VARYING,
    WHERE,
    WINDOW,
    WITH,
    YEAR;

    boolean matches(String source, int start, int end) {
        String text = name();
        return end - start == text.length()
                && source.regionMatches(true, start, text, 0, text.length());
    }

    static SqlKeyword of(String source, int start, int end) {
        for (SqlKeyword keyword : values()) {
            if (keyword.matches(source, start, end))
                return keyword;
        }
        return null;
    }

    public static SqlKeyword of(String text) {
        if (text == null)
            return null;

        for (SqlKeyword keyword : values()) {
            if (keyword.name().equalsIgnoreCase(text))
                return keyword;
        }
        return null;
    }
}
