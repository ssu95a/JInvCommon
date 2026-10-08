package ru.inversion.utils.parser.statement.parts;

public class ParameterRef {

    public enum Type {
        JDBC_POSITIONAL,   // ?
        NAMED,             // :name
        PG_POSITIONAL      // $1
    }

    /** */
    private final Comparable<?> identifier;

    public ParameterRef( Comparable<?> identifier ) {

        this.identifier = identifier;

        if (identifier != null && !(identifier instanceof String || identifier instanceof Integer ) ) {
            throw new IllegalArgumentException("Unsupported parameter identifier: " + identifier);
        }
    }

    /** */
    public Type getType() {

        if( identifier == null )
            return Type.JDBC_POSITIONAL;

        final String s = identifier.toString();
        return s.charAt(0) == '$' ? Type.PG_POSITIONAL : Type.NAMED;
    }

    public String getName() {
        return (String)identifier;
    }

    public Integer getPosition() {
        return (Integer)identifier;
    }
}
