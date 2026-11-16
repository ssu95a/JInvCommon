package ru.inversion.dataset.parser.lexer;

import java.util.TreeMap;

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

    private static final TreeMap<String,SqlKeyword> BY_NAME =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        for( SqlKeyword keyword : values() )
            BY_NAME.put(keyword.name(), keyword);
    }

    public static boolean in( SqlKeyword keyword, SqlKeyword ... values ) {
        for( SqlKeyword value : values )
            if( keyword == value )
                return true;

        return false;
    }

    boolean matches( String source, int start, int end)
    {
        String text = name();
        return end - start == text.length() && source.regionMatches( true, start, text, 0, text.length() );
    }

    static SqlKeyword of(String source, int start, int end) {
        return BY_NAME.get( source.substring(start, end) );
    }

    public static SqlKeyword of(String text) {
        return text == null ? null : BY_NAME.get(text);
    }
}
