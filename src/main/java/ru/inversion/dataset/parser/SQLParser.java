package ru.inversion.dataset.parser;

/**
 * Compatibility facade for the legacy SQLParser public API.
 *
 * @deprecated new code must use the dialect-aware parser classes from this
 * package directly. The facade preserves legacy behavior for existing
 * applications while the implementation remains isolated in {@code old}.
 */
@Deprecated
public class SQLParser extends ru.inversion.dataset.parser.old.SQLParser {

    /** Legacy language selector retained for source and binary compatibility. */
    public enum Lang {
        SQL,
        PL_SQL
    }

    public SQLParser(String sql) {
        super(sql);
    }

    public SQLParser(String sql, Lang lang) {
        super(sql, toLegacyLang(lang));
    }

    /** Compatibility entry point; formatting is implemented by the new utility. */
    public static String value2Str(Object value) {
        return SqlValueFormatter.format(value);
    }

    private static ru.inversion.dataset.parser.old.SQLParser.Lang toLegacyLang(
            Lang lang
    ) {
        if( lang == null )
            throw new NullPointerException("lang");

        switch( lang ) {
            case SQL:
                return ru.inversion.dataset.parser.old.SQLParser.Lang.SQL;
            case PL_SQL:
                return ru.inversion.dataset.parser.old.SQLParser.Lang.PL_SQL;
            default:
                throw new IllegalArgumentException("Unsupported language: " + lang);
        }
    }
}
