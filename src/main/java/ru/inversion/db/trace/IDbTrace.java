package ru.inversion.db.trace;

import ru.inversion.db.JInvDbException;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EventObject;
import java.util.function.Consumer;

/** */
public interface IDbTrace extends AutoCloseable {

   /** */
   public static class TraceEvent extends EventObject {

      public enum Type {
         Trace_On, Trace_Off, Set_FileName, Download_Begin, Download_End
      }

      final private Type type;
      final private String fileName;

      /** {@inheritDoc} */
      public TraceEvent(Object source, Type type, String fileName) {
         super(source);
         this.type     = type;
         this.fileName = fileName;
      }

      /** */
      public TraceEvent(Object source, Type type) {
         super(source);
         this.type     = type;
         this.fileName = null;
      }

      public Type type() {
         return type;
      }

      public String fileName() {
         return fileName;
      }
   }

   /** */
   TaskContext getTaskContext();

   /** */
   boolean isOn( );

   /** */
   void on( int traceLevel );

   /** */
   void off( );

   /** */
   String getTraceFileName();

   /** */
   Path download( );

   /** */
   void makeRemoteDir( );

   /** */
   void addListener( Consumer<TraceEvent> listener );

   /** */
   void removeListener( Consumer<TraceEvent> listener );

   /** */
   static void runDdl( TaskContext tc, String ddlStr, Object ... parameters ) {

      if( S.isNullOrEmpty(ddlStr) )
          return;

      try( PreparedStatement cs = tc.getConnection().prepareCall(ddlStr) ) {

         if( parameters.length > 0 )
            for( int i = 0; i < parameters.length; i++ )
                 cs.setObject( i+1, parameters[i] );

         cs.execute();

      } catch ( SQLException ex ) {
         throw new JInvDbException( ex, ddlStr, Arrays.asList(parameters) );
      }
   }

}
