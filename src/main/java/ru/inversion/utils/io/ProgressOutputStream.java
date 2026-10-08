package ru.inversion.utils.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.LongConsumer;

/** */
public class ProgressOutputStream extends FilterOutputStream {

   private static final long NOTIFY_EVERY_BYTES = 1024L;// * 1024L;

   private final LongConsumer listener;

   private long totalBytes = 0L;
   private long lastNotifyBytes = 0L;

   public ProgressOutputStream(OutputStream out, LongConsumer listener ) {
      super(out);
      this.listener = listener;
   }

   @Override
   public void write(int b) throws IOException {
      out.write(b);
      addBytes(1);
   }

   @Override
   public void write(byte[] b) throws IOException {
      write(b, 0, b.length);
   }

   @Override
   public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
      addBytes(len);
   }
   /** */
   private void addBytes(long bytes)
   {
      totalBytes += bytes;

      if( totalBytes - lastNotifyBytes >= NOTIFY_EVERY_BYTES )
      {
         listener.accept( totalBytes);
         lastNotifyBytes = totalBytes;
      }
   }
}