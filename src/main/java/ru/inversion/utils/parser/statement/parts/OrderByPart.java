package ru.inversion.utils.parser.statement.parts;

import ru.inversion.utils.parser.statement.IStatementPart;
import ru.inversion.utils.parser.statement.SqlStatement;

import java.io.IOException;
import java.io.Writer;

public final class OrderByPart implements IStatementPart {

    private final SqlStatement statement;

    public OrderByPart( SqlStatement statement ) {
        this.statement = statement;
    }

    @Override
    public void appendTo( Writer out ) throws IOException {
        out.write(" ORDER BY ");
        statement.appendTo(out);
    }

    @Override
    public String toString() {
        return "ORDER_BY: " + statement;
    }
}
