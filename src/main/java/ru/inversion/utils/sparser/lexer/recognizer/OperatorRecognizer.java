package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.OPERATOR;

/**
 * Лексемы операторов. Не разбирает приоритеты и не проверяет существование
 * операторов в БД.
 *
 * Oracle: известные символьные комбинации.
 * PostgreSQL: scanner only separates operator text from parameters/comments.
 * It does not validate whether a particular custom operator exists.
 *
 * Input is JDBC SQL. A single ? is always a positional parameter.
 * Question marks inside PostgreSQL operators must use pgJDBC escaping (??).
 */
final class OperatorRecognizer implements TokenRecognizer {

    private static final String PG_CHARS = "+-*/<>=~!@#%^&|`?";
    private static final String ORACLE_SINGLE = "+-*/<>=";
    private static final String[] ORACLE_DOUBLE = {
            "<=", ">=", "<>", "!=", "^=", "~=", "||", "**", "=>"
    };

    private final boolean postgres;

    OperatorRecognizer(boolean postgres) {
        this.postgres = postgres;
    }

    @Override
    public SqlScanner.Token recognize(String sql, int at) {
        if (at + 1 < sql.length() && sql.charAt(at) == ':'
                && (sql.charAt(at + 1) == ':' || sql.charAt(at + 1) == '=')) {
            return new SqlScanner.Token(OPERATOR, at, at + 2);
        }
        return postgres ? postgresOperator(sql, at) : oracleOperator(sql, at);
    }

    private static SqlScanner.Token oracleOperator(String sql, int at) {
        for (String candidate : ORACLE_DOUBLE) {
            if (at + candidate.length() <= sql.length()
                    && sql.regionMatches(at, candidate, 0, candidate.length())) {
                return new SqlScanner.Token(OPERATOR, at, at + candidate.length());
            }
        }
        return ORACLE_SINGLE.indexOf(sql.charAt(at)) >= 0
                ? new SqlScanner.Token(OPERATOR, at, at + 1) : null;
    }

    private static SqlScanner.Token postgresOperator(String sql, int at) {
        if (!operatorChar(sql.charAt(at))) return null;

        // A single ? belongs to CommonRecognizers.Parameter.
        if (sql.charAt(at) == '?' && !ScanChars.follows(sql, at, '?'))
            return null;

        int end = at;

        while (end < sql.length() && operatorChar(sql.charAt(end))) {
            char ch = sql.charAt(end);

            // Comment recognizers have priority over operator continuation.
            if (ch == '-' && ScanChars.follows(sql, end, '-')) break;
            if (ch == '/' && ScanChars.follows(sql, end, '*')) break;

            if (ch == '?') {
                // pgJDBC represents one SQL operator ? as ??.
                if (!ScanChars.follows(sql, end, '?')) break;
                end += 2;
                continue;
            }

            end++;
        }

        return end == at
                ? null
                : new SqlScanner.Token(OPERATOR, at, end);
    }

    private static boolean operatorChar(char ch) {
        return PG_CHARS.indexOf(ch) >= 0;
    }
}
