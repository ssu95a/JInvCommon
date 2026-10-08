package ru.inversion.utils.parser.statement;

import java.util.Map;

import java.util.*;

import static ru.inversion.utils.ParametersValues.ParamIdComparator;

public class StatementContext {

    private int jdbcIndex = 0;

    /** name → jdbc index
        position ($1) → jdbc index */
    private final Map<Comparable<?>, Integer> indexes = new TreeMap<>( ParamIdComparator );

    /** flags */
    boolean hasWhere;
    boolean hasOrderBy;
    boolean hasSelect;

    /** */
    int nextJdbcIndex() {
        return ++jdbcIndex;
    }

    int bindNamed( String name ) {
        return indexes.computeIfAbsent(name, k -> nextJdbcIndex());
    }

    int bindPg( int position ) {
        return indexes.computeIfAbsent( position, k -> nextJdbcIndex());
    }

    public int getJdbcParameterCount() {
        return jdbcIndex;
    }
}
