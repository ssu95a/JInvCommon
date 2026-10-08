package ru.inversion.utils.parser.statement;

import java.io.IOException;
import java.io.Writer;

public final class AdditionalPredicatePart implements IStatementPart {

    private final SqlStatement predicate;

    public AdditionalPredicatePart( SqlStatement predicate ) {
        this.predicate = predicate;
    }

    @Override
    public void appendTo(Writer out ) throws IOException {
        out.write(" AND ");
        predicate.appendTo( out );
    }

    @Override
    public String toString() {
        return "ADDITIONAL_PREDICATE: " + predicate;
    }
}
