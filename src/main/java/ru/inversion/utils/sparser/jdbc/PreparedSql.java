package ru.inversion.utils.sparser.jdbc;

import ru.inversion.utils.Checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable JDBC SQL plan.
 * <p>
 * Parameter at index {@code i} describes JDBC placeholder {@code i + 1}.
 * The list is a physical placeholder order only; it carries no fragment
 * ownership. Runtime values are resolved immediately before execution.
 */
public final class PreparedSql {

    private final String sql;

    private final List<Parameter> parameters;

    public PreparedSql( String sql, List<Parameter> parameters )
    {
        this.sql = Checks.Require.text( sql, "sql");

        if( parameters == null || parameters.isEmpty() )
            this.parameters = Collections.emptyList();
        else
        {
           List<Parameter> copy = new ArrayList<>( parameters );
           this.parameters = Collections.unmodifiableList(copy);
        }
    }

    /** */
    public String getSql( ) {
        return sql;
    }

    /**
     * Immutable snapshot of parameters in physical JDBC placeholder order.
     */
    public List<Parameter> getParameters() {
        return parameters;
    }
}
