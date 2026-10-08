package ru.inversion.tc;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.inversion.login.Logon;
import ru.inversion.utils.ConnectionStringFormatEnum;

import java.net.URL;

public class Test_TaskContext {

    // вызывается один раз на класс-тест перед запуском теста
    @BeforeAll
    static void init() {
        Logon.login( new String[] {"--ru.inversion.userid=FWFhJL0NBU1BFUkBkZXY4aQ=="});
    }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }

    //
    @Test
    //@Ignore
    @DisplayName( value = "Test multi DB connection" )
    void testMultiConnection( ) {

        TaskContext tc = new TaskContext( );

        System.out.println( tc.getConnectionString(ConnectionStringFormatEnum.JDBC));

        tc = new TaskContext( "GO","qqq", "ver8i" );

        System.out.println( tc.getConnectionString(ConnectionStringFormatEnum.JDBC));

    }
}
