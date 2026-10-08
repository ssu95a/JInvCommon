package ru.inversion.utils;

import org.junit.jupiter.api.*;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Test_S {

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
    @DisplayName( value = "Test S.lastChar( String ) function" )
    void testLastChar() {
        assertEquals( S.lastChar( "123"), '3' );
        assertEquals( S.lastChar( "1"), '1' );
        assertEquals( S.lastChar( null ), 0 );
        assertEquals( S.lastChar( "" ), (char)0 );
    }

    @Test
    @Disabled
    @DisplayName( value = "Test S.in( String, String, ... ) function" )
    void test_In()
    {
        assertTrue  ( S.inIgnoreCase( "test", "Test", "1", "2", "X ", " test ", " x" ) );
        assertTrue  ( S.in( "test", "test", "1", "2", "X ", " test ", " x" ) );
    }

    @Test
    @Disabled
    void likeToRegex()
    {
        Pair<String, String> t1 = new Pair<>("text", "^text$");
        Pair<String, String> t2 = new Pair<>(null, null);
        Pair<String, String> t3 = new Pair<>("%text%", "^.*text.*$");
        Pair<String, String> t4 = new Pair<>("_text", "^.text$");
        Pair<String, String> t5 = new Pair<>("^.*_text%$", "^\\^\\.\\*.text.*\\$$");

        Stream.of(t1,t2,t3,t4,t5).forEach(pair -> {
            String converted = S.likeToRegexp(pair.first);
            //logger.info("original: {}, converted: {}, expected: {}", pair.first, converted, pair.second);
            Assertions.assertEquals( pair.second, converted );
        });
    }

//    final String n = "ООО «Рога и копыта»";
//    assertFalse  ( S.translate( n, "«» ", "\"\"_").equals(n) );

}
