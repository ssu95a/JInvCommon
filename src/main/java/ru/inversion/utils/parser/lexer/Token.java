package ru.inversion.utils.parser.lexer;

import ru.inversion.utils.parser.text.SourceText;
import ru.inversion.utils.parser.text.TextRange;

import java.util.Objects;

/**
 * Один непрерывный лексический фрагмент исходного текста.
 */
public final class Token<K extends TokenKind> {

    private final K kind;
    private final TextRange range;

    public Token( K kind, TextRange range )
    {
        this.kind  = Objects.requireNonNull( kind, "kind"  );
        this.range = Objects.requireNonNull( range, "range");
    }

    /** */
    public K kind() {
        return kind;
    }

    /** */
    public TextRange range() {
        return range;
    }

    /** */
    public String text( SourceText source )
    {
        Objects.requireNonNull(source, "source");
        return source.substring( range.start(), range.end() );
    }

    /** */
    @Override
    public String toString() {
        return kind + " " + range;
    }
}