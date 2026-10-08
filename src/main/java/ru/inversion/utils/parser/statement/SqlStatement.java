package ru.inversion.utils.parser.statement;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class SqlStatement implements IStatementPart {

    private final List<IStatementPart> parts;

    public SqlStatement( List<IStatementPart> parts )
    {
        this.parts = parts;
    }

    /** */
    public void appendTo( Writer out ) throws IOException {
        for( IStatementPart p : parts ) {
             p.appendTo(out);
        }
    }
}
