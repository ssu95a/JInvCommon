package ru.inversion.utils.parser.lexer;

import ru.inversion.utils.parser.text.SourceText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * <h5>Результат лексического разбора.</h5>
 * <p>
 * Связывает исходный текст и токены, диапазоны которых
 * относятся именно к этому тексту.
 */
public final class LexerResult<K extends TokenKind> {

    private final SourceText source;
    private final List<Token<K>> tokens;

    public LexerResult( SourceText source, List<Token<K>> tokens )
    {
        this.source = Objects.requireNonNull(source, "source");
        this.tokens = Collections.unmodifiableList( new ArrayList<>(Objects.requireNonNull(tokens, "tokens")) );
    }

    public SourceText source() {
        return source;
    }

    public List<Token<K>> tokens() {
        return tokens;
    }

    public String text(Token<K> token) {
        Objects.requireNonNull(token, "token");
        return source.substring( token.range().start(), token.range().end() );
    }
}