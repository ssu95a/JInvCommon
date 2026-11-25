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

    private static final TreeMap<String,SqlKeyword> BY_NAME = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private static final int MAX_LENGTH;

    static {

        int l = 0;

        for( SqlKeyword keyword : values() ) {
             BY_NAME.put(keyword.name(), keyword);

             if( keyword.name().length() > l )
                 l = keyword.name().length();
        }

        MAX_LENGTH = l;

    }

    public static boolean in( SqlKeyword keyword, SqlKeyword ... values )
    {
        for( SqlKeyword value : values )
            if( keyword == value )
                return true;

        return false;
    }

    /** */
    boolean matches( String source, int start, int end)
    {
        return matches(this, source, start, end );
    }

    /** */
    private static boolean matches( SqlKeyword k, String source, int start, int end)
    {
        int l = end - start;

        if( l > MAX_LENGTH )
            return false;

        final String text = k.name();

        return l == text.length() && source.regionMatches( true, start, text, 0, l );
    }

    /** */
    static SqlKeyword of( String source, int start, int end ) {
        return end - start <= MAX_LENGTH ? null : BY_NAME.get( source.substring(start, end) );
    }

    /** */
    public static SqlKeyword of(String text) {
        return text == null || text.isEmpty() || text.length() > MAX_LENGTH ? null : BY_NAME.get(text);
    }
}
