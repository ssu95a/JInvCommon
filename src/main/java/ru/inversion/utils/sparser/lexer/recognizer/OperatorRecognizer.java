package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.OPERATOR;

/**
 * Лексемы операторов. Не разбирает приоритеты и не проверяет существование
 * операторов в БД.
 *
 * Oracle: известные символьные комбинации.
 * PostgreSQL: операторные символы по правилам SQL; пользовательские
 * операторы не требуют обновления списка известных имён.
 *
 * ВАЖНО: вход — SQL для JDBC. Одинарный ? всегда позиционный параметр.
 * Знаки ? внутри операторов PostgreSQL должны быть удвоены (pgJDBC: ??).
 */
final class OperatorRecognizer implements TokenRecognizer {

    private static final String PG_CHARS = "+-*/<>=~!@#%^&|`?";
    private static final String PG_SPECIAL = "~!@#%^&|`?";
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

        // ? without doubling is a JDBC parameter, not an SQL operator.
        if (sql.charAt(at) == '?' && !ScanChars.follows(sql, at, '?')) return null;

        int i = at;
        boolean hasSpecial = false;
        while (i < sql.length() && operatorChar(sql.charAt(i))) {
            char ch = sql.charAt(i);

            // SQL comment start wins over an operator continuation.
            if (ch == '-' && ScanChars.follows(sql, i, '-')) break;
            if (ch == '/' && ScanChars.follows(sql, i, '*')) break;

            if (ch == '?') {
                // pgJDBC escape is always ??, including within custom operators.
                if (!ScanChars.follows(sql, i, '?')) break;
                hasSpecial = true;
                i += 2;
                continue;
            }
            if (PG_SPECIAL.indexOf(ch) >= 0) hasSpecial = true;
            i++;
        }
        if (i == at) return null;

        // PostgreSQL does not allow a multi-character non-special operator
        // to end in + or - (e.g. '*-' is '*' followed by '-').
        if (!hasSpecial) {
            while (i - at > 1 && (sql.charAt(i - 1) == '+' || sql.charAt(i - 1) == '-')) {
                i--;
            }
        }
        return new SqlScanner.Token(OPERATOR, at, i);
    }

    private static boolean operatorChar(char ch) {
        return PG_CHARS.indexOf(ch) >= 0;
    }
}
