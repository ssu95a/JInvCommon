package ru.inversion.utils.sparser.lexer.recognizer;

/** Small character-level helpers without SQL grammar semantics. */
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
        return Character.isLetterOrDigit(ch)
                || ch == '_' || ch == '$' || ch == '#';
    }

    static boolean atPostgresWordBoundary(String sql, int at) {
        return at == 0
                || !(identifierPart(sql.charAt(at - 1))
                && sql.charAt(at - 1) != '#');
    }

    static int quotedEnd(String sql, int quoteAt, char quote) {
        for (int i = quoteAt + 1; i < sql.length(); i++) {
            if (sql.charAt(i) != quote)
                continue;

            if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                i++;
                continue;
            }

            return i + 1;
        }

        throw unterminated("quoted text", quoteAt);
    }

    static IllegalArgumentException unterminated(String what, int start) {
        return new IllegalArgumentException(
                "Unterminated " + what + " at offset " + start);
    }
}
