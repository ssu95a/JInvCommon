package ru.inversion.utils.parser.lexer.recognizer;


import ru.inversion.utils.parser.lexer.TokenKind;
import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт ровно один UTF-16 code unit.
 *
 * Обычно используется как fallback-правило, гарантирующее
 * продвижение лексического движка.
 */
public final class SingleCharacterRecognizer<K extends TokenKind>
        implements TokenRecognizer<K> {

    private final K kind;

    public SingleCharacterRecognizer(K kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public TokenMatch<K> match(
            SourceText source,
            int offset
    ) {
        Objects.requireNonNull(source, "source");

        if (offset < 0 || offset >= source.length()) {
            return null;
        }

        return new TokenMatch<K>(
                kind,
                offset + 1
        );
    }
}