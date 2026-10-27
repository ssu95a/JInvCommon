package ru.inversion.utils.sparser.jdbc;

import ru.inversion.utils.Checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SQL для JDBC и источники значений его параметров.
 * <p>
 * Элемент parameters с индексом i соответствует JDBC-позиции i + 1.
 * Значения runtime-параметров разрешаются непосредственно перед execute.
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

    /** */
    public List<Parameter> getParameters() {
        return parameters;
    }
}
