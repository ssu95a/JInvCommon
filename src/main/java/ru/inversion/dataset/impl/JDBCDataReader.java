package ru.inversion.dataset.impl;

import ru.inversion.dataset.*;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.db.JInvDbException;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.utils.Checks;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.dataset.parser.jdbc.PreparedSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 *
 * Размер JDBC-пакета задаётся положительным системным свойством
 * {@code ru.inversion.db.dataset.fetch_size}, по умолчанию 60.
 * @author ssu @
 */
public class JDBCDataReader<T> implements ISQLDataReader<T>, AutoCloseable {
    /** */
    private static class ObjectArrayRowMapper implements IRSRowMapper<Object[]> {
        private final ResultSet rs;
        private final int nColumn;
        private ObjectArrayRowMapper( ResultSet rs ) throws SQLException {
            this.rs = rs;
            this.nColumn = rs.getMetaData().getColumnCount();
        }

        @Override
        public Object[] mapRow( int rowNum ) throws SQLException {
            Object[] retRow = new Object[nColumn];
            for( int i = 0, j = 1 ; i < nColumn; i++ )
                retRow[i] = rs.getObject(j++);
            return retRow;
        }
        @Override
        public ResultSet getResultSet() {
            return rs;
        }
    }

    private static final int DEFAULT_FETCH_SIZE = 60;
    private static final String FETCH_SIZE_PROPERTY = "ru.inversion.db.dataset.fetch_size";

    private ResultSet rowSet;
    private PreparedStatement statement;

    private IRowMapper<T> rowMapper;
    private boolean isOwnerMapper = false;

    private final Connection connection;

    private int rowCounter = 0;
    private boolean enableMark = false;

    private final Class<T> rowClass;

    private final PreparedSql preparedSql;

    /** Подготовленный SQL-запрос, созданный с помощью sparser; значения определяются при каждом выполнении */
    public JDBCDataReader (
        Connection connection,
        PreparedSql preparedSql,
        IRowMapper<T> rowMapper,
        Class<T> rowClass,
        boolean enableMark)
    {
        if( rowMapper == null && rowClass == null )
            throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "'rowMapper' and 'rowClass' both null!");

        this.connection  = Checks.Require.object( connection,  "connection" );
        this.preparedSql = Checks.Require.object( preparedSql, "preparedSql");

        this.rowMapper   = rowMapper;
        this.rowClass    = rowClass;

        this.enableMark  = enableMark;
    }

    /** */
    @Deprecated
    @Override
    public void executeQuery( IParameters callbackParameters ) throws DataSetException
    {
        try {
            executeQuery( PreparedSqlValues.jdbc(preparedSql, callbackParameters) );
        }
        catch( DataSetException ex ) {
            throw ex;
        }
        catch( Exception ex ) {
            closeAfterFailure(ex);
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error on dataReader executeQuery", ex);
        }
    }

    /** Выполнить с установленными значениями параметров, в заданном порядке. */
    @Override
    public synchronized void executeQuery( List<Object> jdbcValues ) throws DataSetException {
        try {

            closeOrThrow();

            rowCounter = 0;

            if( jdbcValues == null || jdbcValues.size() != preparedSql.getParameters().size() )
                throw new IllegalArgumentException( "PreparedSql parameters/value count mismatch");

            final List<Object> pv = new ArrayList<>(jdbcValues);

            try {

                statement = connection.prepareStatement( preparedSql.getSql() );

                int nIndex = 1;

                for( Object value : pv )
                     statement.setObject( nIndex++, value );

                int fetchSize = Integer.getInteger(FETCH_SIZE_PROPERTY, DEFAULT_FETCH_SIZE);
                statement.setFetchSize(fetchSize > 0 ? fetchSize : DEFAULT_FETCH_SIZE);

                rowSet = statement.executeQuery();
            }
            catch( SQLException ex ) {
                throw new JInvDbException(ex, preparedSql.getSql(), pv);
            }

            if( !rowSet.next() )
                closeOrThrow();
            else
                initMapper();
        }
        catch( Exception ex ) {
            closeAfterFailure(ex);
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error on dataReader executeQuery", ex );
        }
    }

    /** */
    private void initMapper() throws SQLException
    {
        if( this.rowMapper == null )
        {
            if( rowClass == null || rowClass == Object[].class )
                this.rowMapper = (IRowMapper<T>)new ObjectArrayRowMapper( rowSet );
            else
                this.rowMapper = EntityMetadataFactory.getEntityMetaData(rowClass).createRSRowMapper(rowSet);

            isOwnerMapper = true;
        }
    }

    /** */
    private SQLException closeResources() {

        SQLException failure = null;

        ResultSet currentRowSet = rowSet;
        rowSet = null;

        if( currentRowSet != null )
        {
            try {
                currentRowSet.close();
            }
            catch( SQLException ex ) {
                failure = ex;
            }
        }

        PreparedStatement currentStatement = statement;
        statement = null;

        if( currentStatement != null )
        {
            try {
                currentStatement.close();
            }
            catch( SQLException ex ) {
                if( failure == null )
                    failure = ex;
                else
                    failure.addSuppressed(ex);
            }
        }

        if( isOwnerMapper )
            rowMapper = null;

        return failure;
    }

    /** */
    private void closeOrThrow() throws SQLException {

        SQLException failure = closeResources();

        if( failure != null )
            throw failure;
    }

    /** */
    private void closeAfterFailure(Throwable failure) {

        SQLException closeFailure = closeResources();

        if( closeFailure != null )
            failure.addSuppressed(closeFailure);
    }

    @Override
    public boolean isEOF() {
        return rowSet == null;
    }

    @Override
    public List<T> getNextPage( int nRowCount ) {
        try {
            if( isEOF() )
                return Collections.emptyList();
            return U.toList( U.iterable( new RSIterator( nRowCount ) ) );
        }
        catch( Exception e ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on create pageIterator", e );
        }
    }

    @Override
    public Iterator<T> getNextDataPart( int nRowCount ) {
        if( isEOF() )
            return Collections.emptyIterator();
        return new RSIterator( nRowCount );
    }

    @Override
    public void close() throws DataSetException {

        SQLException failure = closeResources();

        if( failure != null )
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error on dataReader close", failure);
    }

    /** */
    private class RestoreContext {

        final private T row;
        final private int nCounter;

        private RestoreContext( T row, int nCounter ) {
            this.row = row;
            this.nCounter = nCounter;
        }
    }

    /** */
    private class RSIterator implements Iterator<T> {

        private int nCounter = -1;

        public RSIterator( int nRowCount ) {
            nCounter = nRowCount;
        }

        @Override
        synchronized public boolean hasNext()
        {
            try {
                return nCounter != 0 && !isEOF() && !rowSet.isClosed();
            }
            catch( SQLException ex ) {
                closeAfterFailure(ex);
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on check ResultSet state", ex);
            }
        }

        /**
         * Восстановление неблокирующего курсора PostgreSQL,
         * ставшего недействительным из-за транзакции
         */
        private int restore( RestoreContext ctx ) {

            try {

                ResultSet currentRowSet = rowSet;
                rowSet = null;

                if( currentRowSet != null )
                    currentRowSet.close();

                connection.rollback();

                rowSet = statement.executeQuery();

                final Comparator<T> comparator = EntityMetadataFactory.getEntityMetaData(rowClass).getIdComparator();

                if( isOwnerMapper )
                {
                    rowMapper = null;
                    initMapper();
                }

                boolean found = false;

                int restoredRow = 0;

                while( rowSet.next() )
                {
                    T candidate = rowMapper.mapRow( rowSet, restoredRow++);

                    if( comparator.compare(ctx.row, candidate) == 0 ) {
                        found = true;
                        break;
                    }
                }

                if( !found )
                    throw new IllegalStateException( "Can not restore ResultSet position after SQLSTATE 34000");

                /* переходим на след строку */
                if( !rowSet.next() ) {
                    closeOrThrow();
                    return 0;
                }

                return ctx.nCounter - 1;
            }
            catch( Throwable th ) {
                closeAfterFailure(th);
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on restore resultSet after PG '34000' code", th );
            }
        }

        @Override
        synchronized public T next()
        {
            T row = null;

            try {
                row = rowMapper.mapRow( rowSet, rowCounter++ );

                if( enableMark )
                    ((IMarkable) row).setMark( rowSet.getBoolean(IMarkable.MARK_COLUMN) );

                if( !rowSet.next() )
                {
                    SQLException closeFailure = closeResources();
                    nCounter = 0;

                    if( closeFailure != null )
                        throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on dataReader close at EOF", closeFailure );
                }
                else
                    nCounter--;
            }
            catch ( SQLException ex ) {
                if( "34000".equals( ex.getSQLState() ) )
                    nCounter = restore( new RestoreContext( row, nCounter ) );
                else {
                    closeAfterFailure(ex);
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on read Row from resultSet", ex);
                }
            }
            catch ( RuntimeException | Error ex ) {
                closeAfterFailure(ex);
                throw ex;
            }
            return row;
        }
    }
}
