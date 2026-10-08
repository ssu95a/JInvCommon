package ru.inversion.db.trace;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import ru.inversion.db.JInvDbException;

/** */
public class PostgresDbTrace extends DbTraceBase {

   private boolean stateOn = false;

   /** */
   public PostgresDbTrace( TaskContext tc ) {
      super(tc);
   }

   @Override
   public boolean isOn() {
      return stateOn;
   }

   /** */
   @Override
   protected void doOn( int traceLevel ) {
      try {
        PgTraceHelper.start (getTaskContext (), getTaskContext ().getUserName ());
      } catch ( SQLException e ) {
         throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on execute DB statement", e );
      }
        
      stateOn = true;
   }

   /** */
//   private Optional<String> getComment( String relname )
//   {
//      try( CallableStatement cs = getTaskContext().getConnection().prepareCall("{call fxbicomp.fore_support$TraceComment (?, ?)}") )
//      {
//         cs.setString(1, relname );
//         cs.registerOutParameter(2, Types.VARCHAR );
//
//         cs.execute();
//
//         String comment = cs.getString(2);
//
//         if( !S.isNullOrEmpty(comment) )
//             return Optional.of(comment);
//      }
//      catch( Exception e ) {
//         e.printStackTrace();
//      }
//      return Optional.empty();
//   }

   /** */
   @Override
   protected void doOff( )
   {
//      try( CallableStatement cs = getTaskContext().getConnection().prepareCall("{call fxbicomp.fore_support$TraceStop (?, ?, ?)}") )
//      {
//         cs.setString(1, getTaskContext().getUserName() );
//         cs.setNull  (2, Types.VARCHAR );
//
//         cs.registerOutParameter(3, Types.VARCHAR );
//
//         cs.execute();
//
//         setTraceFileName(cs.getString(3) );
      try 
      {
         setTraceFileName (PgTraceHelper.stop (getTaskContext (), getTaskContext ().getUserName (), null));
      }
      catch( SQLException e ) {
         throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on execute DB statement", e );
      }
      catch( Exception e ) {
         throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on off trace", e );
      }
      finally {
         stateOn = false;
      }
   }

   /**
     * @param notifier
     * @return  */
   @Override
   public Path doDownload( BiConsumer<Boolean,String> notifier ) {

      notifier.accept(true, getTraceFileName() );

      try {
         return downloadData();
      }
      finally {
         notifier.accept(false, getTraceFileName() );
      }
   }

   @Override
   public void makeRemoteDir( ) {
      throw new UnsupportedOperationException("makeRemoteDir");
   }

   /** */
   private Path downloadData( )  {

      try {

         Checks.Require.text( getTraceFileName(),"traceFileName" );

         final String trcFn = getTraceFileName();

//         final PGConnection pgConnection = getTaskContext().getConnection().unwrap(PGConnection.class);
//         final CopyManager  copyManager  = pgConnection.getCopyAPI();
//
//         Path traceFile = Paths.get( System.getProperty("java.io.tmpdir"), trcFn + ".zip" );
//
//         try( ZipOutputStream fw = new ZipOutputStream( Files.newOutputStream( traceFile)) )
//         {
//            ZipEntry pss = new ZipEntry ("pgpro_stats_statements");
//
//            getComment( getTraceFileName() ).ifPresent(pss::setComment);
//
//            fw.putNextEntry (pss);
//
//            long l = copyManager.copyOut ("COPY xxi_stat.\"" + trcFn + "\" TO STDOUT WITH (FORMAT binary)", fw );
//
//            fw.closeEntry ();
//         }
//         return traceFile;
            return PgTraceHelper.download (getTaskContext (), trcFn).toPath ();
      }
      catch( Exception e ) {
         throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on downloadData from postgres", e );
      }
   }
}
