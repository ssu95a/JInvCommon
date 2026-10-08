package ru.inversion.utils.parser.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenKind;
import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт фиксированную последовательность символов.
 *
 * Примеры:
 *   (
 *   ;
 *   <=
 *   ||
 */
public final class FixedTextRecognizer<K extends TokenKind> implements TokenRecognizer<K> {

    private final String text;
    private final K kind;

    public FixedTextRecognizer(String text, K kind)
    {
        this.text = Objects.requireNonNull(text, "text");
        this.kind = Objects.requireNonNull(kind, "kind");

        if( text.isEmpty() )
            throw new IllegalArgumentException("text is empty");
    }

    @Override
    public TokenMatch<K> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        for( int index = 0; index < text.length(); index++)
        {
            if( source.get(offset + index) != text.charAt(index))
                return null;
        }

        return new TokenMatch<K>( kind, offset + text.length() );
    }

    @Override
    public String toString() { return "FixedTextRecognizer[" + text + " -> " + kind + "]"; }
}