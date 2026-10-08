package ru.inversion.dataset.impl;

import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.dataset.mark.MarkDescriptor;
import ru.inversion.dataset.mark.MarkModeEnum;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.db.DBUniqueResult;
import ru.inversion.db.JInvDbException;
import ru.inversion.db.StoredProc;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.NamedNativeQuery;
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
        String         strSQLForMark,
        IParameters    callbackParameters,
        boolean        savePrevMarkedRows
    )
        throws DataSetException
    {

        if( U.containsNull(markerID, markDescriptor, strSQLForMark ) || markDescriptor.getMarkMode() == MarkModeEnum.NONE )
            return;

        try {

            if( !savePrevMarkedRows )
                unMarkAllRow( tc, markerID, markDescriptor );

            List<Object> parameters = new ArrayList<>( );

            SQLParser sqlParser = new SQLParser( strSQLForMark );
            String sql = sqlParser.prepareActualSql( callbackParameters, parameters );

            try( CallableStatement stMark = tc.getConnection().prepareCall(sql) ) {

                int i = 1;

                for( Object o : parameters ) {
                    stMark.setObject(i++, Jt2St.convert(o));
                }
                stMark.execute( );

                tc.getConnection().commit();

            } catch ( Throwable th ) {
                try { tc.getConnection().rollback(); } catch (SQLException ex) { } ;
                throw new JInvDbException( th, strSQLForMark, parameters );
            }
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "markAllRow", th );
        }
    }

    /** */
    static public void unMarkAllRow (
        TaskContext    tc,
        MarkDescriptor markDescriptor,
        String         strSQLForUnMark,
        IParameters    callbackParameters
    )
        throws DataSetException
    {

        if( U.containsNull( markDescriptor, strSQLForUnMark ) || markDescriptor.getMarkMode() == MarkModeEnum.NONE )
            return;

        try {


            List<Object> parameters = new ArrayList<>( );

            SQLParser sqlParser = new SQLParser( strSQLForUnMark );
            String sql = sqlParser.prepareActualSql( callbackParameters, parameters );

            try( CallableStatement stMark = tc.getConnection().prepareCall(sql) ) {

                int i = 1;

                for( Object o : parameters )
                    stMark.setObject( i++, Jt2St.convert( o ) );

                stMark.execute( );

                tc.getConnection().commit();

            } catch ( Throwable th ) {
                try { tc.getConnection().rollback(); } catch (SQLException ex) { } ;
                throw new JInvDbException( th, strSQLForUnMark, parameters );
            }
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "unMarkAllRow", th );
        }
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
