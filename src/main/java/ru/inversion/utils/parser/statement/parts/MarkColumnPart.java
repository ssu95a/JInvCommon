package ru.inversion.utils.parser.statement.parts;

import ru.inversion.utils.parser.statement.IStatementPart;

import java.io.IOException;
import java.io.Writer;

public final class MarkColumnPart implements IStatementPart {

    private final String column;

    public MarkColumnPart(String column) {
        this.column = column;
    }

    @Override
    public void appendTo(Writer out) throws IOException {

    }
}
