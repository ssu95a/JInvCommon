package ru.inversion.utils.parser.statement.parts;

import ru.inversion.utils.parser.statement.IStatementPart;

import java.io.IOException;
import java.io.Writer;

public class ParameterPart implements IStatementPart  {

    final private int          jdbcIndex;
    final private ParameterRef parameterRef;

    /** */
    public ParameterPart( ParameterRef parameterRef, int jdbcIndex )
    {
        this.parameterRef = parameterRef;
        this.jdbcIndex    = jdbcIndex;
    }

    /** */
    public int jdbcIndex( ) {
        return jdbcIndex;
    }

    /** */
    public ParameterRef parameterRef( ) {
        return parameterRef;
    }

    /** */
    @Override
    public void appendTo( Writer out ) throws IOException {
        out.write("? ");
    }
}
