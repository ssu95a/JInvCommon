package ru.inversion.utils.parser.sql.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PreparedSql {

    private final String sql;

    /**
     * Все параметры в порядке JDBC-позиций.
     */
    private final List<SqlParameterBinding> bindings;

    /**
     * Имя -> все JDBC-позиции этого имени.
     */
    private final Map<String, List<Integer>> namedPositions;

    /** */
    public PreparedSql( String sql, List<SqlParameterBinding> bindings )
    {
        this.sql = Objects.requireNonNull( sql, "sql" );
        Objects.requireNonNull( bindings, "bindings" );

        List<SqlParameterBinding> bindingCopy = new ArrayList<SqlParameterBinding>( bindings.size() );

        for( int index = 0; index < bindings.size(); index++)
        {
            SqlParameterBinding binding = Objects.requireNonNull( bindings.get(index), "bindings[" + index + "]" );

            int expectedJdbcPosition    = index + 1;

            if( binding.jdbcPosition() != expectedJdbcPosition )
                throw new IllegalArgumentException( "Expected JDBC position " + expectedJdbcPosition + ", actual " + binding.jdbcPosition() );

            bindingCopy.add(binding);
        }

        this.bindings = Collections.unmodifiableList( bindingCopy );
        this.namedPositions =   createNamedPositions( bindingCopy );
    }

    /** */
    public String sql() {
        return sql;
    }

    /** */
    public List<SqlParameterBinding> bindings() {
        return bindings;
    }

    /** */
    public Map<String, List<Integer>> namedPositions() {
        return namedPositions;
    }

    /** */
    public List<Integer> positionsOf( String name )
    {
        Objects.requireNonNull( name, "name" );

        List<Integer> positions = namedPositions.get(name);

        if( positions == null )
            return Collections.emptyList();

        return positions;
    }

    /** */
    private static Map<String, List<Integer>> createNamedPositions( List<SqlParameterBinding> bindings )
    {
        Map<String, List<Integer>> mutable = new LinkedHashMap<String, List<Integer>>();

        for( SqlParameterBinding binding : bindings )
        {
            if(!binding.isNamed() )
                continue;

            String name = binding.name();

            List<Integer> positions = mutable.computeIfAbsent( name, k -> new ArrayList<>() );
            positions.add( binding.jdbcPosition() );
        }

        Map<String, List<Integer>> result = new LinkedHashMap<String, List<Integer>>();

        for( Map.Entry<String, List<Integer>> entry : mutable.entrySet())
             result.put( entry.getKey(), Collections.unmodifiableList( new ArrayList<Integer>( entry.getValue() ) ) );

        return Collections.unmodifiableMap( result );
    }
}