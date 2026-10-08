package ru.inversion.utils;

import org.junit.jupiter.api.*;
import ru.inversion.utils.io.SegmentedBAOS;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Test_U {

    // вызывается один раз на класс-тест перед запуском теста
    @BeforeAll
    static void init() {
    }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }

    @Test
    @Disabled
    @DisplayName( value = "Test U.decode(...) function" )
    void testDecode() {

        IllegalArgumentException
        ex = assertThrows(
                IllegalArgumentException.class,
                () -> U.decode(1, 1),
                "Exception on U.decode"
        );

        assertNull(U.decode(null, 1, null));

        assertEquals(U.decode(null, null, 1, 1, 2), Integer.valueOf(1));

        assertEquals(U.decode(1, 1, "1", 2, "2"), "1");

        //assertEquals(U.decode(3, 1, "1", 2, "2"), null);

        assertEquals(U.decode(3, 1, "1", 2, "2", "X"), "X", "Error on ...");

    }

    @Disabled
    @Test
    @DisplayName( value = "testParseArgsStr" )
    void testParseArgsStr() {
        //assertEquals( U.parseArgsStr("REPORT_TYPE_ID=200998 REPORT_ID=1"), U.toMap("REPORT_TYPE_ID", "200998", "REPORT_ID", "1") );

        System.out.println(
            U.parseArgsStr("ru.inversion.fxpdoc.action.ActionShowOperlist Where=\"dTrnCreate = util.current_date ()\" WhereTitle=\"Приветственный заголовок\"")
        );
    }


    @Test
    @DisplayName( value = "regEep" )
    void testRegExp() {

        String[] strArr = "1+2*4+7-4*45".replaceAll("\\+","#+#").replaceAll("-","#+#").replaceAll("\\*","#+#").split("#");

        System.out.println(
            Arrays.toString  ( strArr )
        );
    }


    @Disabled
    @DisplayName( value = "SegmentBAOS" )
    void test_SegmentBAOS() throws IOException {
        final byte[] bytes = "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 ".getBytes();
        System.out.println("bytes.length " + bytes.length );
        System.out.println("bytes: " + Arrays.toString(bytes));

        ByteArrayOutputStream baos = new ByteArrayOutputStream(10);
        SegmentedBAOS sbaos = new SegmentedBAOS(3000);

        final Path icoPath = new File("d:\\logo.ico").toPath();

        Files.copy( icoPath, sbaos);
//
//        for( int i = 0; i < 50; i++ ) {
//            sbaos.write( bytes );
//            baos.write ( bytes );
//        }
//
//        baos.write ( 32 );
//        baos.write ( 33 );
//        sbaos.write( 32 );
//        sbaos.write( 33 );

//        final byte[] byteArray = baos.toByteArray();
//        assertArrayEquals( sbaos.toByteArray(), byteArray );

        try(final InputStream sis = sbaos.inputStream();
            final InputStream bis = Files.newInputStream( icoPath ) )
        {
            int b_sis = 0;
            int b_bis = 0;

//            while( b_bis != -1 )
//            {
//                b_sis = sis.read();
//                b_bis = bis.read();
//                assertEquals( b_bis, b_sis );
//            }

            byte[] a_sis = new byte[3000];
            byte[] a_bis = new byte[3000];

            b_sis = sis.read(a_sis);
            b_bis = bis.read(a_bis);

            System.out.println( "bytes num b_sis:" + b_sis );
            System.out.println( "bytes num b_bis:" + b_bis );

            assertEquals( b_bis, b_sis, "Кол-во прочитанных байтов" );

            assertArrayEquals( a_sis, a_bis, "Содержимое" );

        }
    }

}