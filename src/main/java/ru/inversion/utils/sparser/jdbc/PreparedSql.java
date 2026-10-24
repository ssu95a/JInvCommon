package ru.inversion.utils.sparser.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SQL для JDBC и источники значений его параметров.
 *
 * Элемент parameters с индексом i соответствует JDBC-позиции i + 1.
 * Значения runtime-параметров разрешаются непосредственно перед execute.
 */
public final class PreparedSql {

    private final String sql;
    private final List<Parameter> parameters;

    public PreparedSql(String sql, List<Parameter> parameters) {
        this.sql = Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(parameters, "parameters");

        List<Parameter> copy = new ArrayList<>(parameters.size());
        for (Parameter parameter : parameters) {
            copy.add(Objects.requireNonNull(parameter, "parameter"));
        }
        this.parameters = Collections.unmodifiableList(copy);
    }

    public String getSql() {
        return sql;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }
}
