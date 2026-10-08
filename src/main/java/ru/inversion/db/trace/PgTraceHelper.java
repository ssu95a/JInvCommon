/*
 * ЦАБС "БАНК 21 ВЕК"
 * Компания ИНВЕРСИЯ
 */
package ru.inversion.db.trace;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import ru.inversion.tc.TaskContext;

/**
 *
 * @author psh
 */
public class PgTraceHelper 
{
    public static void start ( TaskContext tc, String rolname ) throws SQLException
    {
        try ( CallableStatement cs = tc.getConnection ().prepareCall ("{call fxbicomp.fore_support$TraceStart (?)}") ) 
        {
            cs.setString (1, rolname);
            cs.execute ();
        }
    }        
    
    public static String stop ( TaskContext tc, String rolname, String comment ) throws SQLException
    {
        try ( CallableStatement cs = tc.getConnection ().prepareCall ("{call fxbicomp.fore_support$TraceStop (?, ?, ?)}") ) 
        {
            cs.setString (1, rolname);
            cs.setString (2, comment);
            cs.registerOutParameter (3, Types.VARCHAR);
            cs.execute ();
            
            return cs.getString (3);
        }
    }        
    
    public static String getComment ( TaskContext tc, String relname ) throws SQLException
    {        
        try ( CallableStatement cs = tc.getConnection ().prepareCall ("{call fxbicomp.fore_support$TraceComment (?, ?)}") )
        {
            cs.setString (1, relname);
            cs.registerOutParameter (2, Types.VARCHAR);

            cs.execute();

            return cs.getString (2);
        }
    }    
    
    public static File download ( TaskContext tc, String relname ) throws Exception
    {
        final CopyManager cm = tc.getConnection ().unwrap (PGConnection.class).getCopyAPI ();

        Path traceFile = Paths.get (System.getProperty ("java.io.tmpdir"), relname + ".zip");

        try ( ZipOutputStream fw = new ZipOutputStream (Files.newOutputStream (traceFile)) )
        {
            ZipEntry pss = new ZipEntry ("pgpro_stats_statements");

            String comment = getComment (tc, relname);
            if (comment != null)
                pss.setComment (comment);

            fw.putNextEntry (pss);

            long l = cm.copyOut ("COPY xxi_stat.\"" + relname + "\" TO STDOUT WITH (FORMAT binary)", fw);

            fw.closeEntry ();
        }
        
        return traceFile.toFile ();
    }        
    
    public static String upload ( TaskContext tc, File fi ) throws Exception
    {
        String relname = FilenameUtils.removeExtension (fi.getName ());
        
        final CopyManager cm = tc.getConnection ().unwrap (BaseConnection.class).getCopyAPI ();
        
        try (ZipFile zf = new ZipFile (fi))
        {
            ZipEntry pss = Objects.requireNonNull (zf.getEntry ("pgpro_stats_statements"), "Entry not found: pgpro_stats_statements");
            
            try ( CallableStatement cs = tc.getConnection ().prepareCall ("{call fxbicomp.fore_support$TraceUploadInit (?, ?)}") ) 
            {
                cs.setString (1, relname);
                cs.setString (2, pss.getComment ());
                cs.execute ();
            }
            
            try (InputStream fr = zf.getInputStream (pss))
            {
                long l = cm.copyIn ("COPY xxi_stat.\"" + relname + "\" FROM STDIN WITH (FORMAT binary)", fr);
            }
        }            
        
        return relname;
    }    
}
