package ru.inversion.utils.parser.statement.parts;

import ru.inversion.utils.parser.statement.IStatementPart;

import java.io.IOException;
import java.io.Writer;

public final class TextPart implements IStatementPart {

    private final char[] text;

    public TextPart( char[] text ) {
        this.text = text;
    }

    @Override
    public String toString() {
        return new String(text);
    }

    @Override
    public void appendTo(Writer out) throws IOException {
        out.write(text);
    }
}
