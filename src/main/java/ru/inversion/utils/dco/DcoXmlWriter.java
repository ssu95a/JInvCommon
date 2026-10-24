package ru.inversion.utils.dco;

import org.w3c.dom.Element;
import ru.inversion.utils.Checks;
import ru.inversion.utils.Tags;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Последовательно записывает отдельные Dco-узлы
 * внутрь одного XML root element.
 * <p>
 * Не thread-safe.
 */
public final class DcoXmlWriter implements AutoCloseable, Consumer<IDco> {

   private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

   private final Writer       writer;
   private final boolean      closeWriter;
   private final Transformer  transformer;
   private final StreamResult result;
   private final String       rootName;

   private boolean closed;
   private long    count;

   private DcoXmlWriter (
      Writer writer,
      boolean closeWriter,
      String rootName,
      Charset charset,
      boolean declaration
   )
   {
      this.writer      = Checks.Require.object( writer, "writer" );
      this.rootName    = Checks.Require.text(rootName, "rootName");
      this.closeWriter = closeWriter;

      Charset effectiveCharset = charset == null ? DEFAULT_CHARSET : charset;

      try {

         transformer = Dco.transformerFactory().newTransformer();
         transformer.setOutputProperty( OutputKeys.METHOD, "xml" );
         transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );
         transformer.setOutputProperty( OutputKeys.ENCODING, effectiveCharset.name() );

         result = new StreamResult(writer);

         if( declaration )
         {
            writer.write("<?xml version=\"1.0\" encoding=\"");
            writer.write(effectiveCharset.name());
            writer.write("\"?>");
         }

         writer.write('<');
         writer.write(this.rootName);
         writer.write('>');
      }
      catch( Throwable th ) {
         throw new DcoException( Tags.PRODUCT_LABEL + "Error on open streaming XML writer", th );
      }
   }

   /** Создаёт файл, принадлежащий writer-у. */
   public static DcoXmlWriter open( Path path, String rootName )
   {
      return open( path, rootName, DEFAULT_CHARSET );
   }

   /** Создаёт файл, принадлежащий writer-у. */
   public static DcoXmlWriter open( Path path, String rootName, Charset charset )
   {
      Checks.Require.object( path, "path" );

      Charset effectiveCharset = charset == null ? DEFAULT_CHARSET : charset;

      Writer writer = null;

      try {

         writer = Files.newBufferedWriter(
            path,
            effectiveCharset,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
         );

         return new DcoXmlWriter(
            writer,
            true,
            rootName,
            effectiveCharset,
            true
         );
      }
      catch( Throwable th ) {

         if( writer != null)
            try {
               writer.close();
            } catch (IOException ignored) { }

         throw new DcoException( Tags.PRODUCT_LABEL + "Error on open XML file: " + path, th );
      }
   }

   /** Создаёт файл, принадлежащий writer-у. */
   public static DcoXmlWriter open( File file, String rootName, Charset charset )
   {
      Checks.Require.object( file, "file" );
      return open( file.toPath(), rootName, charset );
   }

   /**
    * Использует caller-owned OutputStream.
    * При close() поток не закрывается, а только flush-ится.
    */
   public static DcoXmlWriter wrap( OutputStream stream, String rootName, Charset charset )
   {
      Objects.requireNonNull(stream, "'stream' is null");

      Charset effectiveCharset = charset == null ? DEFAULT_CHARSET : charset;

      Writer writer = new BufferedWriter( new OutputStreamWriter( stream, effectiveCharset ) );
      return new DcoXmlWriter( writer, false, rootName, effectiveCharset, true );
   }

   /**
    * Последовательно пишет один Dco как дочерний XML element.
    * <p>
    * Переданный Dco нигде не сохраняется.
    */
   public DcoXmlWriter write( IDco source )
   {
      ensureOpen();

      Checks.Require.object( source, "source" );

      if( !(source instanceof Dco) )
          throw new DcoException( Tags.PRODUCT_LABEL + "Streaming XML supports only Dco instances" );

      Dco dco = (Dco)source;

      try {

         Element element = dco.xmlElement();
         transformer.transform( new DOMSource(element), result );

         count++;

         return this;
      }
      catch( Throwable th ) {
         throw new DcoException( Tags.PRODUCT_LABEL + "Error on append Dco to XML stream", th );
      }
   }

   /** Позволяет использовать writer прямо в Stream.forEach(...). */
   @Override
   public void accept( IDco source ) {
      write(source);
   }

   /** Количество успешно записанных Dco. */
   public long count() {
      return count;
   }

   /** Не закрывает документ. */
   public void flush()
   {
      ensureOpen();

      try {
         writer.flush();
      }
      catch( Throwable th ) {
         throw new DcoException( Tags.PRODUCT_LABEL + "Error on flush XML stream", th );
      }
   }

   /** Закрывает root element и XML document. */
   @Override
   public void close()
   {
      if( closed )
          return;

      closed = true;

      Throwable failure = null;

      try {
         writer.write("</");
         writer.write(rootName);
         writer.write('>');
         writer.flush();
      }
      catch( Throwable th ) {
         failure = th;
      }

      if( closeWriter )
      {
         try {
            writer.close();
         }
         catch( Throwable th ) {
            if( failure == null )
                failure = th;
            else
                failure.addSuppressed(th);
         }
      }

      if( failure != null )
          throw new DcoException( Tags.PRODUCT_LABEL + "Error on close XML stream", failure );
   }

   private void ensureOpen() {
      if( closed )
         throw new IllegalStateException( "'DcoXmlWriter' is closed" );
   }

}
