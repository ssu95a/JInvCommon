package ru.inversion.utils.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.inversion.utils.S;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestIO {

    @DisplayName( value = "SegmentBAOS" )
    void test_SegmentBAOS() throws IOException {

        final byte[] bytes = "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 ".getBytes();
        System.out.println("bytes.length " + bytes.length    );
        System.out.println("bytes: " + Arrays.toString(bytes));

        //ByteArrayOutputStream baos = new ByteArrayOutputStream(10);
        SegmentedBAOS sbaos = new SegmentedBAOS(512);

        final Path icoPath = new File("d:\\logo.ico").toPath();

        Files.copy( icoPath, sbaos);

        try(final InputStream sis = sbaos.inputStream();
            final InputStream bis = Files.newInputStream( icoPath ) )
        {
            int b_sis = 0;
            int b_bis = 0;

            byte[] a_sis = new byte[12];
            byte[] a_bis = new byte[12];

            do {

                b_sis = sis.read(a_sis);
                b_bis = bis.read(a_bis);

                sis.read();
                bis.read();

                System.out.println( "bytes num b_sis:" + b_sis );
                System.out.println( "bytes num b_bis:" + b_bis );

                assertEquals( b_bis, b_sis, "Кол-во прочитанных байтов" );

                assertArrayEquals( a_sis, a_bis, "Содержимое" );

            } while( b_sis > 0 );

        }
    }

    @DisplayName( value = "SegmentCAW" )
    @Test
    void test_SegmentCAW() throws IOException {

        final char[] chrs = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', ' ', 'a', 'b', 'c'};
        System.out.println( "chrs.length " + chrs.length );
        System.out.println( "chrs: " + Arrays.toString(chrs) );

        CharArrayWriter cawr = new CharArrayWriter(500);
        SegmentedCAW scaw = new SegmentedCAW(50);
        /*
        for( int i = 0; i < chrs.length; i++ ) {
            cawr.write( chrs[i] );
            cawr.write( '\n' );
            scaw.write( chrs[i] );
            scaw.write( '\n' );
        }
        cawr.write( "Simple string" );
        cawr.write( '\n' );
        cawr.write( "Test string" );
        cawr.write( ' ' );
        cawr.write( '\n' );

        scaw.write( "Simple string" );
        scaw.write( '\n' );
        scaw.write( "Test string" );
        scaw.write( ' ' );
        scaw.write( '\n' );

        cawr.write( chrs );
        scaw.write( chrs );

        cawr.write( chrs, 1, 4 );
        scaw.write( chrs, 1, 4 );
        */

        //

        Path filePath = Paths.get("d:\\Temp\\sm0082391410866690607665.log");

        try// (Writer writer = scaw)
        {
            Files.lines(filePath).filter(S::isNotNullOrEmpty).forEach(line -> {
                try {
                    scaw.write(line);
                    scaw.write('\n');
                    cawr.write(line);
                    cawr.write('\n');
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            //System.out.println("Содержимое файла:\n" + writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //assertArrayEquals( cawr.toCharArray(), scaw.toCharArray(), "Содержимое" );

        final Reader scaw_r = scaw.reader();
        final Reader cawr_r = new CharArrayReader( cawr.toCharArray() );

        char[] buf_s = new char[150];
        char[] buf_c = new char[150];

        int l_s;
        int l_c;

        while( true )
        {
            l_c = cawr_r.read( buf_c );
            if( l_c == -1 )
                break;

            if( l_c < buf_c.length )
                System.out.println("<");

            l_s = scaw_r.read( buf_s );
            if( l_s == -1 )
                break;


            if( l_c != l_s )
                System.out.println("not eqv");

            assertEquals( l_s, l_c, "Длины прочитанного" );

            if( l_c < buf_c.length )
                buf_c = Arrays.copyOf(buf_c, l_c );

            if( l_s < buf_s.length )
                buf_s = Arrays.copyOf(buf_s, l_s );

            try {
                assertArrayEquals( buf_s, buf_c, "Reader" );
            }catch( Throwable th ) {
                th.printStackTrace();
            }
        }

    }

}
