package ru.inversion.utils.parser.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenKind;
import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт непрерывную последовательность пробельных символов.
 */
public final class WhitespaceRecognizer<K extends TokenKind> implements TokenRecognizer<K> {

    private final K kind;

    /** */
    public WhitespaceRecognizer( K kind ) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public TokenMatch<K> match(SourceText source, int offset)
    {
        Objects.requireNonNull(source, "source");

        int character = source.get(offset);

        if( !isWhitespace(character) )
            return null;

        int end = offset + 1;

        while( isWhitespace(source.get(end)) )
               end++;


        return new TokenMatch<K>(kind, end);
    }

    private static boolean isWhitespace(int character) {
        if (character == SourceText.EOF) {
            return false;
        }

        char value = (char) character;

        return Character.isWhitespace(value)
                || Character.isSpaceChar(value);
    }
}