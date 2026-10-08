package ru.inversion.utils.parser.statement;

import java.io.IOException;
import java.io.Writer;

public interface IStatementPart {
    void appendTo( Writer out ) throws IOException;
}
