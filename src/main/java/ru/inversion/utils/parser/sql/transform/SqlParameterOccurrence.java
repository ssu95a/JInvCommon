package ru.inversion.utils.parser.sql.transform;

import ru.inversion.utils.parser.text.TextRange;

import java.util.Objects;

public final class SqlParameterOccurrence {

    private final SqlParameterKind kind;
    private final TextRange range;
    private final String name;

    public SqlParameterOccurrence( SqlParameterKind kind, TextRange range, String name )
    {
        this.kind  = Objects.requireNonNull( kind,  "kind" );
        this.range = Objects.requireNonNull( range, "range");

        if( kind == SqlParameterKind.POSITIONAL )
        {
            if( name != null )
                throw new IllegalArgumentException( "Positional parameter cannot have a name" );
        }
        else
        {
            if( name == null || name.isEmpty() )
                throw new IllegalArgumentException( "Named parameter must have a name" );
        }

        this.name = name;
    }

    public SqlParameterKind kind() {
        return kind;
    }

    public TextRange range() {
        return range;
    }

    public boolean isPositional() {
        return kind == SqlParameterKind.POSITIONAL;
    }

    public boolean isNamed() {
        return kind == SqlParameterKind.NAMED;
    }

    /**
     * Имя без двоеточия.
     * Для позиционного параметра возвращает null.
     */
    public String name() {
        return name;
    }
}