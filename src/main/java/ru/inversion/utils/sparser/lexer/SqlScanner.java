package ru.inversion.utils.sparser.lexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Общий цикл лексического разбора SQL.
 *
 * Не изменяет исходный текст и не интерпретирует структуру SELECT.
 * Каждый символ принадлежит ровно одному токену; диапазоны [start, end).
 * Набор правил и их порядок определяются конкретным диалектом.
 */
public abstract class SqlScanner {

    public enum Kind {
        WORD,
        NUMBER,
        STRING,
        QUOTED_IDENTIFIER,
        COMMENT,
        HINT,
        PARAMETERIZE_OFF,
        PARAMETERIZE_ON,
        WHITESPACE,
        POSITIONAL_PARAMETER,
        NAMED_PARAMETER,
        NUMBERED_PARAMETER,
        OPERATOR,
        SYMBOL
    }

    public static final class Token {

        private final Kind kind;

        private final int  start;
        private final int  end;

        // Конструирование токенов доступно только внутри пакета сканнера.
        public Token( Kind kind, int start, int end )
        {
            this.kind = Objects.requireNonNull(kind, "kind");

            if( start < 0 || end <= start )
                throw new IllegalArgumentException("Invalid token range: " + start + ".." + end);

            this.start = start;
            this.end   = end;
        }

        public Kind getKind() { return kind; }
        public int getStart() { return start; }
        public int getEnd() { return end; }

        /** Оригинальный текст лексемы, включая кавычки, маркеры и комментарии. */
        public String text(String source) {
            return source.substring(start, end);
        }

        public boolean isWord(String source, String expected) {
            return kind == Kind.WORD && end - start == expected.length() && source.regionMatches(true, start, expected, 0, expected.length());
        }

        /** Parser-level classification for WORD tokens. */
        public SqlKeyword keyword(String source) {
            return kind == Kind.WORD ? SqlKeyword.of(source, start, end) : null;
        }

        public boolean isKeyword(String source, SqlKeyword keyword) {
            return kind == Kind.WORD
                    && keyword != null
                    && keyword.matches(source, start, end);
        }

        @Override
        public String toString() {
            return kind + "[" + start + ", " + end + ")";
        }
    }

    private final List<TokenRecognizer> recognizers;

    SqlScanner(List<TokenRecognizer> recognizers) {
        Objects.requireNonNull(recognizers, "recognizers");
        if (recognizers.isEmpty()) {
            throw new IllegalArgumentException("Empty recognizer list");
        }
        List<TokenRecognizer> copy = new ArrayList<>(recognizers.size());
        for (TokenRecognizer recognizer : recognizers) {
            copy.add(Objects.requireNonNull(recognizer, "recognizer"));
        }
        this.recognizers = Collections.unmodifiableList(copy);
    }

    public final List<Token> scan(String sql) {

       Objects.requireNonNull(sql, "sql");

        List<Token> result = new ArrayList<>();

        int offset = 0;

        while( offset < sql.length() )
        {
            Token token = recognize(sql, offset);

            if( token == null )
                throw new IllegalArgumentException(
                        "Unsupported SQL syntax at offset " + offset
                        + ": '" + sql.charAt(offset) + "'");

            if (token.getStart() != offset || token.getEnd() > sql.length() )
                throw new IllegalStateException("Recognizer returned invalid token at " + offset + ": " + token);

            result.add(token);

            offset = token.getEnd();
        }
        return Collections.unmodifiableList(result);
    }

    private Token recognize(String sql, int offset) {
        for (TokenRecognizer recognizer : recognizers) {
            Token token = recognizer.recognize(sql, offset);
            if (token != null) {
                return token;
            }
        }
        return null;
    }
}
