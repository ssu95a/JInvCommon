package ru.inversion.db.trace;

import ru.inversion.tc.TaskContext;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class DbTraceBase implements IDbTrace {

   /** */
   final private TaskContext tc;

   /** */
   private String traceFileName;

   /** */
   private IListenerManEvent<Consumer<TraceEvent>,TraceEvent> listeners;

   /** */
   public DbTraceBase( TaskContext tc ) {
      this.tc = tc;
   }

   /** */
   public TaskContext getTaskContext()
   {
      return tc;
   }

   public void setTraceFileName( String fileName )
   {
      this.traceFileName = fileName;
      fire_FileName();
   }

   @Override
   public String getTraceFileName() {
      return traceFileName;
   }

   /** */
   abstract protected void doOn( int traceLevel );

   @Override
   public void on( int traceLevel ) {
      if( !isOn() ) {
         doOn(traceLevel);
         fire(true);
      }
   }

   /** */
   abstract protected void doOff( );

   @Override
   public void off() {
      if( isOn() ) {
         doOff();
         fire(false);
      }
   }

   /** */
   abstract protected Path doDownload( BiConsumer<Boolean,String> notifier );

   @Override
   public Path download() {
      return doDownload(new BiConsumer<Boolean, String>() {
         @Override
         public void accept(Boolean begin, String fileName) {
            if(begin)
               fire_Begin(fileName);
            else
               fire_End(fileName);
         }
      });
   }

   /** */
   @Override
   public void addListener( Consumer<TraceEvent> listener ) {

      if( listener == null )
         return;

      if( listeners == null )
         listeners = ListenerManFactory.createListenerManEvent( Consumer<TraceEvent>::accept );

      listeners.addListener(listener);
   }

   @Override
   public void removeListener(Consumer<TraceEvent> listener) {

      if( listener == null || listeners == null )
         return;
      listeners.removeListener(listener);
      if( listeners.isEmpty() )
         listeners = null;
   }

   /** */
   protected void fire( boolean on ) {
      if( listeners == null )
         return;
      listeners.fire( new TraceEvent(this, on ? TraceEvent.Type.Trace_On : TraceEvent.Type.Trace_Off ) );
   }

   /** */
   protected void fire_Begin( String fileName ) {
      if( listeners == null )
         return;
      listeners.fire( new TraceEvent(this, TraceEvent.Type.Download_Begin, fileName ));
   }

   /** */
   protected void fire_End( String fileName ) {
      if( listeners == null )
         return;
      listeners.fire( new TraceEvent(this, TraceEvent.Type.Download_End, fileName ));
   }

   /** */
   protected void fire_FileName() {
      if( listeners == null )
         return;
      listeners.fire( new TraceEvent(this, TraceEvent.Type.Set_FileName, this.traceFileName ));
   }

   /** */
   @Override
   public void close() {
      off();
   }
}
