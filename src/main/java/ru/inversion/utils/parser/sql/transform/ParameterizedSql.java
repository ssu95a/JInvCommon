package ru.inversion.utils.parser.sql.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Результат замены SQL-литералов на JDBC-параметры.
 *
 * <p>Содержит только значения литералов, заменённых
 * текущим проходом параметризации.</p>
 */
public final class ParameterizedSql {

    private final String sql;
    private final List<Object> parameters;

    public ParameterizedSql(
            String sql,
            List<?> parameters
    ) {
        this.sql =
                Objects.requireNonNull(
                        sql,
                        "sql"
                );

        Objects.requireNonNull(
                parameters,
                "parameters"
        );

        List<Object> copy =
                new ArrayList<Object>(
                        parameters.size()
                );

        for (int index = 0;
             index < parameters.size();
             index++) {

            copy.add(
                    parameters.get(index)
            );
        }

        this.parameters =
                Collections.unmodifiableList(
                        copy
                );
    }

    public String sql() {
        return sql;
    }

    public List<Object> parameters() {
        return parameters;
    }
}