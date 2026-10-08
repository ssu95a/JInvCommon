package ru.inversion.datacall;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;

/** */
public class TestDataCall {

    // вызывается один раз на класс-тест перед запуском теста
    @BeforeAll
    static void init() {
    }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }

    @Test
    //@Ignore
    @DisplayName( value = "Test parsing XML" )
    void testXMLParse( ) {
        final URL defXml = TestDataCall.class.getResource ("plsql/def.xml");
    }

}
