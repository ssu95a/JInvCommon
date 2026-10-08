package ru.inversion.db.trace;


import ru.inversion.db.JInvDbException;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

/**
 *
 * @author ssu
 */
public class OracleDbTrace extends DbTraceBase {

    /** */
    private int traceLevel = -1;

    /** */
    public OracleDbTrace( TaskContext tc ) {
        super(tc);
    }

    /** */
    public boolean isOn( ) {
        return traceLevel > 0;
    }
    
    /** */
    protected void doOn( int traceLevel )
    {
        if( this.traceLevel != traceLevel )
        {
            IDbTrace.runDdl( getTaskContext(),
                      "ALTER SESSION SET EVENTS '10046 trace name context forever, level " + traceLevel + "'" );
            this.traceLevel = traceLevel;
        }
    }
    
    /** */
    protected void doOff( ) {
        IDbTrace.runDdl( getTaskContext(),"begin DBMS_SESSION.Set_Sql_Trace(FALSE); end;" );
        traceLevel = -1;
    }

    /** */
    @Override
    protected Path doDownload( BiConsumer<Boolean,String> notifier ) {

        makeTraceFileName();

        notifier.accept(true, getTraceFileName() );
        try {
            return downloadFile( getTraceFileName() );
        }
        finally {
            notifier.accept(false, getTraceFileName() );
        }
    }

    /** */
    @Override
    public void makeRemoteDir( ) {
        IDbTrace.runDdl( getTaskContext(),"begin UTIL_TRACE.MakeDir(); end;" );
    }

    /** */
    private void makeTraceFileName( ) {

        if( getTraceFileName() == null )
        {
            final String sp_name = "{?=call Get_TraceFile()}";

            try( CallableStatement cs = getTaskContext().getConnection().prepareCall(sp_name) ) {

                cs.registerOutParameter(1, Types.VARCHAR);
                cs.execute( );

                setTraceFileName(cs.getString(1));

            } catch( Exception ex ) {
                throw new JInvDbException(Tags.PRODUCT_LABEL + "Error on get trace file name on server DB", ex, sp_name, null);
            }
        }
    }
    
    /** */
    private long openTraceFile( String traceFileName ) {

        final String sp_name = "{?=call UTIL_TRACE.OpenUDumpFile(?)}";
        
        try( CallableStatement cs = getTaskContext().getConnection().prepareCall(sp_name) ) {
            
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.setString( 2, traceFileName);
            
            cs.execute();
            
            return cs.getLong(1);
            
        } catch( Exception ex ) {
            throw new JInvDbException( Tags.PRODUCT_LABEL + "Error on open trace file on server DB", ex, sp_name, Arrays.asList( traceFileName ) );
        }
    }
    
    /** */
    private Path downloadFile( String trcFN ) {
        
        File tempDir = new File( System.getProperty("java.io.tmpdir") );
        
        openTraceFile( trcFN );
        
        File traceFile = new File( tempDir, trcFN );
        
        try( FileWriter fw = new FileWriter(traceFile); TraceReader tr = new TraceReader() ) {
             
             tr.forEach( (s)->{
                 try {
                     fw.write(s);
                 } catch (IOException ex) {
                    throw new RuntimeException("Error on write trace file '" + traceFile.getAbsolutePath() + "'");
                 }
             } );
             
        } catch (IOException ioex) {
            throw new UncheckedIOException(ioex);
        }
        return traceFile.toPath();
    }

    @Override
    public void close( ) {
        off();
    }
    
    /** */
    private class TraceReader implements Iterable<String>, Iterator<String>, AutoCloseable {
/*

    static final public String exprList[] = {
        "{?=call UTIL_TRACE.ReadNextChunk()}",
        "select OBJECT_ID from all_objects where owner = 'PUBLIC' and object_name = 'XXI_UPGRADE' and OBJECT_TYPE = 'SYNONYM'",
        "insert into XXI_FX_PLSQL_MON (JAR_NAME, SRC_NAME, BLK_NAME) select ?,?,? from dual where not exists ( select null from XXI_FX_PLSQL_MON where SRC_NAME = ? and BLK_NAME = ? and run_date = trunc(LOCALTIMESTAMP) )"
    };


 */

        final private String sp_readNextChunk = "{?=call UTIL_TRACE.ReadNextChunk()}\n--lti";
        
        private CallableStatement cs;
        private String            currentStr;
        
        public TraceReader( ) {
            
            try {
            
                cs = getTaskContext().getConnection().prepareCall( sp_readNextChunk );
                cs.registerOutParameter( 1, Types.VARCHAR );
                
            } catch( SQLException ex ) {
                throw new JInvDbException( Tags.PRODUCT_LABEL + "Error on init sp ", ex, sp_readNextChunk, null );
            }
            
            readNextString( );
        }
        
        /** */
        private void readNextString( ) {

            try {
            
                cs.execute( );

                currentStr = cs.getString(1); 
                
            } catch( SQLException ex ) {
                throw new JInvDbException( Tags.PRODUCT_LABEL + "Error on get data from file on server DB", ex, sp_readNextChunk, null );
            }
        }
        
        /** */
        @Override
        public Iterator<String> iterator() {
            return this;
        }

        @Override
        public void close() {
            
            if( cs != null )
            {
                try {

                    if(!cs.isClosed() )
                        cs.close();

                } catch( SQLException ignored ) {
                }
                
                cs = null;
            }
        }

        @Override
        public boolean hasNext() {
            return currentStr != null;
        }
        
        @Override
        public String next( ) {
            
            if( !hasNext() )
                 throw new NoSuchElementException();
            
            String retStr = currentStr;
            
            readNextString( );
            
            return retStr;
        }
    }
}
