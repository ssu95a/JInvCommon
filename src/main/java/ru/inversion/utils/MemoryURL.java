package ru.inversion.utils;


import org.apache.commons.io.input.ReaderInputStream;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Класс URL для указания на ресурсы объектов в памяти
 * <p>
 * by https://stackoverflow.com/questions/17384783/is-it-possible-to-create-an-url-pointing-to-an-in-memory-object
 * */
public class MemoryURL {

    static private final WeakHashMap<String,Object> contents  = new WeakHashMap<>();
    static private final URLStreamHandler streamHandler       = new MemoryStreamHandler();

    static private final Class<?>[] supportedDataClasses = {
        String.class, char[].class, byte[].class, Reader.class, InputStream.class
    };

    // Более безопасное проверка классов:
    private static boolean isSupportedType(Class<?> clazz) {
        return clazz == String.class ||
               clazz == char[].class ||
               clazz == byte[].class ||
               Reader.class.isAssignableFrom(clazz) ||
               InputStream.class.isAssignableFrom(clazz) ||
               Function.class.isAssignableFrom(clazz) ||
               Supplier.class.isAssignableFrom(clazz);
    }

    /** */
    private static String normalizePath( String path ) {
        return path.charAt(0) == '/' ? path : "/" + path;
    }

    /** */
    public static URL create( String path, Object data ) throws MalformedURLException {

        if( data == null )
            throw new MalformedURLException( Tags.PRODUCT_LABEL + "'data' is null" );

        if( path == null )
            throw new MalformedURLException( Tags.PRODUCT_LABEL + "'path' is null" );

        if( !isSupportedType(data.getClass()) )
            throw new MalformedURLException( Tags.PRODUCT_LABEL + "Bad data type '" + data.getClass().getName() + "' for inMemory URL" );

        String normalizedPath = normalizePath(path);

        URL url = new URL("memory", S.EMPTY_STRING, -1, normalizedPath, streamHandler );

        contents.put( normalizedPath, data );

        return url;
    }

    /** */
    public static URL create( String path ) throws MalformedURLException {
        return new URL("memory", "", path );
    }

    /** */
    public static Object getData( URL url ) {

        if( url == null || !"memory".equals( url.getProtocol() ) )
            return null;

        return contents.get( normalizePath( url.getPath() ) );
    }

    /** */
    public static URL getUrl( String path ) throws MalformedURLException {

        if( S.isNullOrEmpty(path) )
            return null;

        String normalizedPath = normalizePath(path);

        if( !contents.containsKey(normalizedPath) )
             return null;

        return new URL( "memory", "", -1, normalizedPath, streamHandler);
    }

    /** */
    public static class MemoryStreamHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection( URL u ) throws IOException {

            if( !u.getProtocol().equals("memory") )
                throw new IOException("Cannot handle protocol: " + u.getProtocol());

            return new URLConnection(u) {


                private Object data = null;

                @Override
                public String getContentType() {
                    final String path = url.getPath().toLowerCase();
                    if (path.endsWith(".json")) return "application/json";
                    if (path.endsWith(".xml" )) return "application/xml";
                    if (path.endsWith(".txt" )) return "text/plain";
                    return "application/octet-stream";
                }

                @Override
                public void connect() throws IOException {
                    initAndCheckData();
                    // Protected field from superclass
                    connected = true;
                }

                @Override
                public long getContentLengthLong() {

                    if( data == null)
                        data = contents.get( normalizePath(u.getPath()) );

                    if( data == null )
                        return 0;

                    if( data instanceof String )
                        return ((String)data).length();
                    if( data.getClass() == char[].class ) {
                        return ((char[])data).length;
                    }
                    if( data.getClass() == byte[].class ) {
                        return ((byte[])data).length;
                    }

                    return -1;
                }

                private InputStream getInputStream(Object data) throws IOException {

                    if( data instanceof String )
                        return new ByteArrayInputStream( ((String)data).getBytes(StandardCharsets.UTF_8));

                    if( data.getClass() == byte[].class )
                        return new ByteArrayInputStream( ((byte[])data));

                    if( InputStream.class.isAssignableFrom( data.getClass() ) )
                        return (InputStream)data;

                    if( data.getClass() == char[].class ) {
                        return ReaderInputStream.builder().setCharset(StandardCharsets.UTF_8).setReader(new CharArrayReader((char[]) data)).get();
                        //return new ReaderInputStream(new CharArrayReader((char[]) data), StandardCharsets.UTF_8);
                    }

                    if( data instanceof Reader )
                        return ReaderInputStream.builder().setCharset(StandardCharsets.UTF_8).setReader((Reader)data).get();
                        //return new ReaderInputStream((Reader) data, StandardCharsets.UTF_8) ;

                    throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "Not impl cast '" + data.getClass().getName() + "' to InputStream" );
                }

                @Override
                public InputStream getInputStream() throws IOException {

                    initAndCheckData();

                    try {

                        if (Function.class.isAssignableFrom(data.getClass())) {
                            Function<URL, Object> f = (Function<URL, Object>) data;
                            return getInputStream(f.apply(u));
                        }
                        if (Supplier.class.isAssignableFrom(data.getClass())) {
                            Supplier<Object> s = (Supplier<Object>) data;
                            return getInputStream(s.get());
                        }

                        return getInputStream(data);

                    } catch (Exception e) {
                        throw new IOException( Tags.PRODUCT_LABEL + "getInputStream execution failed for: " + u.getPath(), e );
                    }
                }

                private void initAndCheckData() throws IOException {
                    if( data == null)
                        data = contents.get(normalizePath( u.getPath() ));
                    if( data == null )
                        throw new IOException( Tags.PRODUCT_LABEL + "In-memory data cannot be found for: " + u.getPath() );
                }

            };
        }
    }
}
