package ru.inversion.dataset.impl;

import ru.inversion.dataset.*;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.db.JInvDbException;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 *
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

    static private final int currentPageSize = 60;

    private ResultSet rowSet;
    private PreparedStatement statement;
    private IRowMapper<T> rowMapper;
    private boolean isOwnerMapper = false;
    private final Connection connection;
    private int rowCounter = 0;
    private boolean enableMark = false;
    private final Class<T> rowClass;
    private final PreparedSql preparedSql;

    /** Prepared SQL produced by sparser; values are resolved for every execution. */
    public JDBCDataReader(Connection connection, PreparedSql preparedSql,
                          IRowMapper<T> rowMapper, Class<T> rowClass, boolean enableMark) {
        if (!U.containsNotNull(rowMapper, rowClass))
            throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "'rowMapper' and 'rowClass' both null!");

        this.connection = Objects.requireNonNull(connection, "connection");
        this.preparedSql = Objects.requireNonNull(preparedSql, "preparedSql");
        this.rowMapper = rowMapper;
        this.rowClass = rowClass;
        this.enableMark = enableMark;
    }

    /**
     * Compatibility entry point. New internal code should resolve values
     * before crossing the JDBCDataReader boundary.
     */
    @Override
    public void executeQuery( IParameters callbackParameters ) throws DataSetException {
        executeQuery( PreparedSqlValues.jdbc(preparedSql, callbackParameters) );
    }

    /** Execute with values already resolved in physical JDBC placeholder order. */
    @Override
    public synchronized void executeQuery( List<Object> jdbcValues ) throws DataSetException {
        try {
            doClose();
            rowCounter = 0;

            if( jdbcValues == null
                    || jdbcValues.size() != preparedSql.getParameters().size() )
                throw new IllegalArgumentException(
                        "PreparedSql parameters/value count mismatch");

            final List<Object> pv = new ArrayList<>(jdbcValues);

            try {
                statement = connection.prepareStatement( preparedSql.getSql() );

                int nIndex = 1;
                for( Object o : pv )
                    statement.setObject( nIndex++, o );

                statement.setFetchSize( currentPageSize );
                rowSet = statement.executeQuery();
            }
            catch( SQLException ex ) {
                doClose();
                throw new JInvDbException( ex, preparedSql.getSql(), pv );
            }

            if( !rowSet.next() )
                doClose();
            else
                initMapper();
        }
        catch( Exception ex ) {
            doClose();
            throw new DataSetException(
                    Tags.PRODUCT_LABEL + "Error on dataReader executeQuery", ex);
        }
    }

    /** */
    private void initMapper() throws SQLException {
        if( this.rowMapper == null ) {
            if( rowClass == null || rowClass == Object[].class )
                this.rowMapper = (IRowMapper<T>)new ObjectArrayRowMapper( rowSet );
            else
                this.rowMapper = EntityMetadataFactory.getEntityMetaData(rowClass).createRSRowMapper(rowSet);
            isOwnerMapper = true;
        }
    }

    private void doClose() {
        if( rowSet != null ) {
            try { rowSet.close(); } catch( SQLException ex ) { ex.printStackTrace(); }
            rowSet = null;
        }
        if( statement != null ) {
            try { statement.close(); } catch( SQLException ex ) { ex.printStackTrace(); }
            statement = null;
        }
        if( isOwnerMapper )
            rowMapper = null;
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
        doClose();
    }

    private class RestoreContext {
        final private T row;
        final private int nCounter;
        final private SQLException cause;

        private RestoreContext( T row, int nCounter, SQLException cause ) {
            this.row = row;
            this.nCounter = nCounter;
            this.cause = cause;
        }

        public T getRow() { return row; }
        public int getnCounter() { return nCounter; }
        public SQLException getCause() { return cause; }
    }

    private class RSIterator implements Iterator<T> {
        private int nCounter = -1;

        public RSIterator( int nRowCount ) {
            nCounter = nRowCount;
        }

        @Override
        synchronized public boolean hasNext() {
            try {
                if( nCounter == 0 || isEOF() || rowSet.isClosed() )
                    return false;
                return true;
            }
            catch( SQLException ex ) {
                return false;
            }
        }

        private int restore( RestoreContext ctx ) {
            try {
                rowSet.close();
                rowSet = null;
                connection.rollback();
                rowSet = statement.executeQuery();
                final Comparator<T> cmp = EntityMetadataFactory.getEntityMetaData(rowClass).getIdComparator();
                if( isOwnerMapper ) {
                    rowMapper = null;
                    initMapper();
                }
                int n = rowCounter;
                while( rowSet.next() && n > 0 ) {
                    T row = rowMapper.mapRow( rowSet, n++ );
                    if( cmp.compare( ctx.row, row ) == 0 )
                        break;
                    n--;
                }
                if( n != 0 )
                    System.out.println( "oldRowCont = " + rowCounter + ", newRowCont = " + n );
                return ctx.nCounter;
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on restore resultSet after '34000' code", th );
            }
        }

        @Override
        synchronized public T next() {
            T row = null;
            try {
                row = rowMapper.mapRow( rowSet, rowCounter++ );
                if( enableMark )
                    ((IMarkable) row).setMark( rowSet.getBoolean(IMarkable.MARK_COLUMN) );

                if( !rowSet.next() ) {
                    doClose();
                    nCounter = 0;
                }
                else
                    nCounter--;
            }
            catch ( SQLException ex ) {
                if( "34000".equals( ex.getSQLState() ) )
                    nCounter = restore( new RestoreContext( row, nCounter, ex ) );
                else {
                    doClose();
                    throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on read Row from resultSet", ex);
                }
            }
            return row;
        }
    }
}
