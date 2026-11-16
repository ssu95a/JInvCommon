package ru.inversion.db;

import ru.inversion.dataset.IRowMapper;
import ru.inversion.tc.TaskContext;
import ru.inversion.dataset.parser.jdbc.PreparedSql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lightweight streaming query.
 *
 * <p>The returned iterator closes its ResultSet and PreparedStatement when it
 * reaches EOF. Use this DBIterResult in try-with-resources when iteration may
 * stop early.</p>
 *
 * <pre>{@code
 * try (DBIterResult<Object,Person> query =
 *              new DBIterResult<>(tc, Person.class)) {
 *     Iterator<Person> rows = query.table()
 *             .where("STATUS=:status")
 *             .set("status", 1)
 *             .execute();
 *     rows.forEachRemaining(this::accept);
 * }
 * }</pre>
 */
public class DBIterResult<R,V>
        extends AbstractDBResult<R,V,DBIterResult<R,V>> {

    private int fetchSize;
    private Cursor current;

    public DBIterResult(TaskContext tc) {
        super(tc);
    }

    public DBIterResult(TaskContext tc, Class<V> valueClass) {
        this(tc);
        valueClass(valueClass);
    }

    public DBIterResult(
            TaskContext tc,
            Class<R> runClass,
            Class<V> valueClass
    ) {
        this(tc, valueClass);
        runClass(runClass);
    }

    /** Zero keeps the JDBC driver's default fetch size. */
    public DBIterResult<R,V> fetchSize(int fetchSize) {
        if( fetchSize < 0 )
            throw new IllegalArgumentException(
                    "fetchSize must be >= 0");

        this.fetchSize = fetchSize;
        return this;
    }

    /** Open a forward-only streaming iterator. */
    public Iterator<V> execute() {
        close();

        PreparedSql sql = preparedSql();
        List<Object> values = jdbcValues(sql);
        PreparedStatement statement = null;
        ResultSet rs = null;

        try {
            statement = taskContext.getConnection()
                    .prepareStatement(sql.getSql());
            bind(statement, values);

            if( fetchSize > 0 )
                statement.setFetchSize(fetchSize);

            rs = statement.executeQuery();
            IRowMapper<V> rowMapper = createRowMapper(rs);

            Cursor cursor = new Cursor(
                    statement, rs, rowMapper, sql, values);
            statement = null;
            rs = null;
            current = cursor;

            if( !cursor.advance() ) {
                if( throwOnNoDataFound )
                    JInvDbException.throwNoDataFound(
                            sql.getSql(), values);

                return Collections.emptyIterator();
            }

            return cursor;
        }
        catch( Throwable failure ) {
            closeAfterFailure(rs, statement, failure);
            current = null;
            throw executionFailure(failure, sql, values);
        }
    }

    @Override
    public void close() {
        Cursor cursor = current;
        current = null;

        if( cursor != null )
            cursor.close();
    }

    private void cursorClosed(Cursor cursor) {
        if( current == cursor )
            current = null;
    }

    private static void closeAfterFailure(
            ResultSet rs,
            PreparedStatement statement,
            Throwable failure
    ) {
        SQLException closeFailure = closeResources(rs, statement);
        if( closeFailure != null )
            failure.addSuppressed(closeFailure);
    }

    private static SQLException closeResources(
            ResultSet rs,
            PreparedStatement statement
    ) {
        SQLException failure = null;

        if( rs != null ) {
            try {
                rs.close();
            }
            catch( SQLException ex ) {
                failure = ex;
            }
        }

        if( statement != null ) {
            try {
                statement.close();
            }
            catch( SQLException ex ) {
                if( failure == null )
                    failure = ex;
                else
                    failure.addSuppressed(ex);
            }
        }

        return failure;
    }

    private final class Cursor implements Iterator<V> {
        private PreparedStatement statement;
        private ResultSet rs;
        private final IRowMapper<V> rowMapper;
        private final PreparedSql sql;
        private final List<Object> values;

        private int rowNumber;
        private boolean ready;

        private Cursor(
                PreparedStatement statement,
                ResultSet rs,
                IRowMapper<V> rowMapper,
                PreparedSql sql,
                List<Object> values
        ) {
            this.statement = statement;
            this.rs = rs;
            this.rowMapper = rowMapper;
            this.sql = sql;
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            return ready || advance();
        }

        @Override
        public V next() {
            if( !hasNext() )
                throw new NoSuchElementException();

            try {
                V value = rowMapper.mapRow(rs, rowNumber++);
                ready = false;
                return value;
            }
            catch( Throwable failure ) {
                closeAfterFailure(failure);
                throw executionFailure(failure, sql, values);
            }
        }

        private boolean advance() {
            if( ready )
                return true;
            if( rs == null )
                return false;

            try {
                ready = rs.next();
                if( !ready )
                    close();
                return ready;
            }
            catch( Throwable failure ) {
                closeAfterFailure(failure);
                throw executionFailure(failure, sql, values);
            }
        }

        private void closeAfterFailure(Throwable failure) {
            SQLException closeFailure = release();
            if( closeFailure != null )
                failure.addSuppressed(closeFailure);
        }

        private void close() {
            SQLException failure = release();
            if( failure != null )
                throw executionFailure(failure, sql, values);
        }

        private SQLException release() {
            ResultSet currentResultSet = rs;
            PreparedStatement currentStatement = statement;
            rs = null;
            statement = null;
            ready = false;
            cursorClosed(this);
            return closeResources(currentResultSet, currentStatement);
        }
    }
}
