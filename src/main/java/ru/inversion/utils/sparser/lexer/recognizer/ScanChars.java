package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

/** Небольшие общие операции с символами. Не содержит SQL-семантики. */
final class ScanChars {
    private ScanChars() { }

    static boolean follows(String sql, int at, char next) {
        return at + 1 < sql.length() && sql.charAt(at + 1) == next;
    }

    static boolean digit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    static boolean identifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    static boolean identifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '#';
    }

    static boolean atPostgresWordBoundary(String sql, int at) {
        return at == 0 || !(identifierPart(sql.charAt(at - 1)) && sql.charAt(at - 1) != '#');
    }

    /** Для строковых префиксов E, U&, Q, NQ: префикс не часть имени. */
    static boolean atWordBoundary(String sql, int at) {
        return at == 0 || !identifierPart(sql.charAt(at - 1));
    }

    static int quotedEnd(String sql, int quoteAt, char quote,
                         boolean backslashEscapes, int tokenStart) {
        for (int i = quoteAt + 1; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (backslashEscapes && ch == '\\') {
                i++; // следующий символ экранирован
            } else if (ch == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i++; // удвоенная кавычка
                } else {
                    return i + 1;
                }
            }
        }
        throw unterminated("quoted text", tokenStart);
    }

    static IllegalArgumentException unterminated(String what, int start) {
        return new IllegalArgumentException("Unterminated " + what + " at offset " + start);
    }
}
