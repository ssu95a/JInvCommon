package ru.inversion.dataset.impl;

import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.dataset.mark.MarkDescriptor;
import ru.inversion.dataset.mark.MarkModeEnum;
import ru.inversion.db.DBUniqueResult;
import ru.inversion.db.JInvDbException;
import ru.inversion.db.StoredProc;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import javax.persistence.NamedNativeQuery;
import java.math.BigInteger;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author ssu @
 */
public class XXIDsMarkerDao {

    @NamedNativeQuery(name="",query="select JF_PKG_UTIL.MRK_GetNextMarkerID() mrk_id from dual")
    private static class PGetMarkerID extends DBUniqueResult<Long> {}

    /** */
	static public Long createMarkerID( TaskContext tc ) {
        return ( new PGetMarkerID() ).execute( tc.getConnection(), true );
	}

    /** */
    static public <T> void markRow( TaskContext tc, Long markerID, MarkDescriptor markDescriptor, IMarkable row, boolean mark ) {

        if( U.containsNull(markerID, row, markDescriptor) || markDescriptor.getMarkMode() == MarkModeEnum.NONE )
            return;

        String sp_name = null;

            switch( markDescriptor.getMarkMode() ) {
                case MRK:
                    sp_name = mark ? "{ call JF_PKG_UTIL.MRK_Insert( ?, ? ) }" : "{ call JF_PKG_UTIL.MRK_Delete( ?, ? ) }";
                break;
                case MRK_ID:
                case MRK_ID2:
                    sp_name = mark ? "{ call JF_PKG_UTIL.MRK_Insert2( ?, ?, ? ) }" : "{ call JF_PKG_UTIL.MRK_Delete2( ?, ?, ? ) }";
                break;
                case MRK_U:
                    sp_name = mark ? "{ call JF_PKG_UTIL.MRK_Insert3( ?, ? ) }" : "{ call JF_PKG_UTIL.MRK_Delete3( ?, ? ) }";
                break;
            }

        try( CallableStatement storedProc = tc.getConnection().prepareCall(sp_name) ) {

            int i = 1;

            switch( markDescriptor.getMarkMode( ) ) {
                case MRK:
                case MRK_U:
                    storedProc.setObject( i++, row.getMarkID1( ) );
                break;
                case MRK_ID:
                case MRK_ID2:
                    storedProc.setObject( i++, row.getMarkID1( ) );
                    storedProc.setObject( i++, row.getMarkID2( ) );
                break;
            }

            storedProc.setLong( i, markerID  );

            storedProc.execute( );

            tc.commit();

        } catch ( Throwable th ) {
            throw new JInvDbException( Tags.PRODUCT_LABEL + "markRow", th, sp_name, Arrays.asList( row.getMarkID1( ), row.getMarkID2( ), markerID ) );
        }
    }

    /** */
    static public void markAllRow (
        TaskContext    tc,
        Long           markerID,
        MarkDescriptor markDescriptor,
        PreparedSql    sql,
        IParameters    parameters,
        boolean        savePrevMarkedRows
    )
        throws DataSetException
    {
        if( U.containsNull(markerID, markDescriptor, sql) || markDescriptor.getMarkMode() == MarkModeEnum.NONE )
            return;

        try {
            if( !savePrevMarkedRows )
                unMarkAllRow( tc, markerID, markDescriptor );

            executePrepared( tc, sql, parameters );
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "markAllRow", th );
        }
    }

    /** */
    static public void unMarkAllRow (
        TaskContext    tc,
        MarkDescriptor markDescriptor,
        PreparedSql    sql,
        IParameters    parameters
    )
        throws DataSetException
    {
        if( U.containsNull(markDescriptor, sql) || markDescriptor.getMarkMode() == MarkModeEnum.NONE )
            return;

        try {
            executePrepared( tc, sql, parameters );
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "unMarkAllRow", th );
        }
    }

    private static void executePrepared(
            TaskContext tc,
            PreparedSql sql,
            IParameters parameters
    ) {
        List<Object> values = resolveParameters( sql, parameters );

        try( PreparedStatement statement = tc.getConnection().prepareStatement(sql.getSql()) ) {
            int index = 1;
            for( Object value : values )
                statement.setObject( index++, value );

            statement.executeUpdate();
            tc.getConnection().commit();
        }
        catch( Throwable th ) {
            try { tc.getConnection().rollback(); } catch( SQLException ignored ) { }
            throw new JInvDbException( th, sql.getSql(), values );
        }
    }

    private static List<Object> resolveParameters( PreparedSql sql, IParameters parameters ) {
        List<Object> values = new ArrayList<>( sql.getParameters().size() );
        int positionalIndex = 0;

        for( Parameter parameter : sql.getParameters() ) {
            Object value;

            switch( parameter.getKind() ) {
                case POSITIONAL:
                    value = parameters.getParameter( positionalIndex++ );
                    break;
                case NAMED:
                    value = parameters.getParameter( parameter.getName() );
                    break;
                case NUMBERED:
                    value = parameters.getParameter( parameter.getNumber() - 1 );
                    break;
                case LITERAL:
                    value = legacyIntegralLiteral( parameter.getValue() );
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown parameter kind: " + parameter.getKind() );
            }

            values.add( Jt2St.convert(value) );
        }

        return values;
    }

    private static Object legacyIntegralLiteral( Object value ) {
        if( !(value instanceof BigInteger) )
            return value;

        BigInteger integer = (BigInteger)value;

        if( integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                && integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0 )
            return integer.intValue();

        if( integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                && integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0 )
            return integer.longValue();

        return integer;
    }

    /** */
    static public void unMarkAllRow( TaskContext tc, Long markerID, MarkDescriptor markDescriptor ) {

        if( U.containsNull( markerID, markDescriptor ) || markDescriptor.getMarkMode() == MarkModeEnum.NONE )
            return;

        final String sp_name = "{ call JF_PKG_util.MRK_DeleteAll( ?, ? ) }";

        try( CallableStatement storedProc = tc.getConnection().prepareCall(sp_name) ) {

            storedProc.setLong( 1, markerID  );
            storedProc.setInt ( 2, markDescriptor.getMarkMode().toInt() );

            storedProc.execute( );

            tc.getConnection().commit();

        } catch ( Throwable th ) {
            try { tc.getConnection().rollback(); } catch (SQLException ex) { } ;
            throw new JInvDbException( Tags.PRODUCT_LABEL +  "unMarkAllRow", th, sp_name, Arrays.asList( markerID, markDescriptor.getMarkMode().toInt() ) );
        }
    }

    static public void markAll( TaskContext tc, Long markerID, MarkDescriptor markDescriptor, Iterable idList )
    {
        if( U.containsNull( markerID, markDescriptor ) || markDescriptor.getMarkMode() == MarkModeEnum.NONE || idList == null)
            return;

        final String sqlInsert;

        switch( markDescriptor.getMarkMode() ) {
            case MRK:
                sqlInsert = "insert into v_jf_mrk( imrkmarkerid, RMRKROWID ) " +
                            "select ?, ? from dual where not exists ( select null from v_jf_mrk a where a.imrkmarkerid = ? and a.RMRKROWID = ?)";
                break;
            case MRK_ID:
                sqlInsert = "insert into v_jf_mrk_id( Idmarker, IDROW ) " +
                            "select ? ,? from dual where not exists ( select null from v_jf_mrk_id a where a.Idmarker = ? and a.IDROW = ?)";
                break;
            case MRK_U:
                sqlInsert = "insert into v_jf_mrk_u( Idmarker, UROW ) " +
                            "select ? ,? from dual where not exists ( select null from v_jf_mrk_id a where a.Idmarker = ? and a.UROW = ?)";
                break;
            default:
                sqlInsert = null;
        }

        if( sqlInsert == null )
            return;

        //unMarkAllRow( tc, markerID, markDescriptor );
        //
        try( PreparedStatement ps = tc.getConnection().prepareStatement(sqlInsert) )
        {
            int counter = 0;

            for( Object id : idList )
            {
                ps.setLong  ( 1, markerID );
                ps.setObject( 2, id );
                ps.setLong  ( 3, markerID );
                ps.setObject( 4, id );

                ps.addBatch();

                if( counter++ >= 50 )
                {
                    ps.executeBatch();
                    counter = 0;
                }
            }

            // хвост обрабатываем
            if( counter > 0 )
                ps.executeBatch();

            tc.getConnection().commit();
        }
        catch( SQLException ex ) {
            try { tc.getConnection().rollback(); } catch (SQLException ignored) { }
            throw new JInvDbException( ex, sqlInsert );
        }
    }

     /** */
    public static String saveMarkToFilter( TaskContext tc, Long markerID, MarkDescriptor markDescriptor ) {

        if( markDescriptor.getMarkMode() == MarkModeEnum.NONE || markerID == null )
            return null;

        final String sp_name = "JF_PKG_util.FF_SaveMarkToFilter";

        try {

            StoredProc sp = new StoredProc(sp_name);
            sp.registerReturnParameter( Types.INTEGER );
            sp.addParameter( markerID );
            sp.addParameter( markDescriptor.getMarkMode().toInt() );

            sp.execute( tc.getConnection() );

            StringBuilder sbSQL = new StringBuilder("(");

            switch ( markDescriptor.getMarkMode() ) {
                case MRK:
                    sbSQL.append(markDescriptor.getMarkKeyColumns()[0]).append(" in ( select rMrkRowID ");
                    break;
                case MRK_ID:
                    sbSQL.append(markDescriptor.getMarkKeyColumns()[0]).append(" in ( select IDMrkRow ");
                    break;
                case MRK_ID2:
                    sbSQL.append('(').append(markDescriptor.getMarkKeyColumns()[0]).append(',').append(markDescriptor.getMarkKeyColumns()[1]).append(')').append(" in ( select IDMrkRow, IDAMrkRow ");
                    break;
                case MRK_U:
                    sbSQL.append(markDescriptor.getMarkKeyColumns()[0]).append(" in ( select UMrkRow ");
                    break;
            }

            sbSQL.append(" FROM V_JF_MRK_TMP WHERE iMrkMarkerID = ").append(markerID).append("))");

            return sbSQL.toString();

        } catch (Throwable ex) {
            throw new JInvDbException(ex);
        }
    }

    /** */
    static public <T> long getTotalMarkRowCount( TaskContext tc, Long markerID, MarkDescriptor markDescriptor ) {

        if( U.containsNull(markerID, markDescriptor) || markDescriptor.getMarkMode() == MarkModeEnum.NONE )
            return 0L;

        String sp_name = "{?= call JF_PKG_util.MRK_Count( ?, ? )}";

        try( CallableStatement storedProc = tc.getConnection().prepareCall(sp_name) ) {

            storedProc.registerOutParameter(1, Types.BIGINT);
            storedProc.setLong( 2, markerID  );
            storedProc.setInt ( 3, markDescriptor.getMarkMode().toInt() );

            storedProc.execute( );

            return storedProc.getLong(1);

        } catch ( Throwable th ) {
            throw new JInvDbException( Tags.PRODUCT_LABEL + "getTotalMarkRowCount", th, sp_name, Arrays.asList( markerID, markDescriptor.getMarkMode().toInt() ) );
        }
    }
}
