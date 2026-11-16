package ru.inversion.db;

import ru.inversion.dataset.IRowMapper;
import ru.inversion.tc.TaskContext;
import ru.inversion.dataset.parser.jdbc.PreparedSql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight query returning at most the first row.
 *
 * <pre>{@code
 * Optional<Person> person = new DBSingleResult<>(tc, Person.class)
 *         .table()
 *         .where("PERSON_ID=:id")
 *         .set("id", personId)
 *         .execute();
 * }</pre>
 */
public class DBSingleResult<R,V>
        extends AbstractDBResult<R,V,DBSingleResult<R,V>> {

    private Optional<V> result = Optional.empty();

    public DBSingleResult(TaskContext tc) {
        super(tc);
    }

    public DBSingleResult(TaskContext tc, Class<V> valueClass) {
        this(tc);
        valueClass(valueClass);
    }

    public DBSingleResult(
            TaskContext tc,
            Class<R> runClass,
            Class<V> valueClass
    ) {
        this(tc, valueClass);
        runClass(runClass);
    }

    /** Result of the last successful execution. */
    public Optional<V> getLastResult() {
        return result;
    }

    /**
     * Execute the configured SELECT. SQL NULL in a scalar first column is
     * represented by {@link Optional#empty()}, like an empty result set.
     */
    public Optional<V> execute() {
        result = Optional.empty();

        PreparedSql sql = preparedSql();
        List<Object> values = jdbcValues(sql);

        try( PreparedStatement statement =
                     taskContext.getConnection()
                             .prepareStatement(sql.getSql()) ) {
            bind(statement, values);
            statement.setMaxRows(1);

            try( ResultSet rs = statement.executeQuery() ) {
                if( !rs.next() ) {
                    if( throwOnNoDataFound )
                        JInvDbException.throwNoDataFound(
                                sql.getSql(), values);

                    return result;
                }

                IRowMapper<V> rowMapper = createRowMapper(rs);
                V value = rowMapper.mapRow(rs, 0);
                result = Optional.ofNullable(value);
                return result;
            }
        }
        catch( Throwable failure ) {
            throw executionFailure(failure, sql, values);
        }
    }
}
