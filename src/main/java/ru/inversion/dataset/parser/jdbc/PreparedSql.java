package ru.inversion.dataset.parser.jdbc;

import ru.inversion.utils.Checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Неизменяемый план SQL запроса в формате JDBC.
 * <p>
 * Параметр с индексом {@code i} описывает JDBC параметр {@code i + 1}.
 * Список представляет собой только физический порядок заполнителей;
 * он не связан с фрагментами.
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

    /** Неизменяемый снимок параметров в физическом порядке заполнителей JDBC. */
    public List<Parameter> getParameters() {
        return parameters;
    }
}
