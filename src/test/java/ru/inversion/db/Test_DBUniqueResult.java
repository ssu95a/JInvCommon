package ru.inversion.db;

import org.junit.jupiter.api.*;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;

/** */
public class Test_DBUniqueResult {

    @NamedNativeQueries(
        value = {
            @NamedNativeQuery( name="n", query = "SELECT util.MRK_Count2(?) n FROM dual"),
            @NamedNativeQuery( name="n.pg", query = "SELECT util.MRK_Count2(?) n FROM dual")
        }
    )
    static public class CountMarkedRowId extends DBUniqueResult<Long> {}

    @BeforeAll
    static void init() {
    }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }

    @Test
    @Disabled
    @DisplayName( value = "Test Double DB: Ora vs Pg" )
    void testDoubleDB( ) {
        final CountMarkedRowId countMarkedRowId = new CountMarkedRowId();
    }
}
