package ru.inversion.utils.parser.lexer;


import java.util.Objects;

/**
 * Внутренний результат работы TokenRecognizer.
 */
public final class TokenMatch<K extends TokenKind> {

    private final K kind;
    private final int endOffset;

    public TokenMatch(K kind, int endOffset) {
        this.kind      = Objects.requireNonNull(kind, "kind");
        this.endOffset = endOffset;
    }

    public K kind() {
        return kind;
    }

    public int endOffset() {
        return endOffset;
    }

    @Override
    public String toString() {
        return kind + " -> " + endOffset;
    }
}