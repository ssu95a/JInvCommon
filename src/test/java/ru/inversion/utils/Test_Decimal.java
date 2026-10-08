package ru.inversion.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/** */
public class Test_Decimal {

    @Test
    @DisplayName(value = "Test Decimal: stripTrailingZeros")
    public void test_stripTrailingZeros()
    {

        String[] as = { "10.0000", "10.234000", "11.0900", "11" };

        for( String s : as )
        {
            BigDecimal d = new BigDecimal(s);
            System.out.println( "str: " + s + ", dec: " + d );
            d = d.stripTrailingZeros();
            System.out.println( "str: " + s + ", dec: " + d );
        }

        BigDecimal d1 = new BigDecimal("11");
        BigDecimal d2 = new BigDecimal("11.00");
        BigDecimal d3 = new BigDecimal("11.11");
        BigDecimal d4 = new BigDecimal("11.1100");

        System.out.println( "d1: " + d1 + ", d2: " + d2 );
    }
}
