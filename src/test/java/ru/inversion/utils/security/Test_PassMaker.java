package ru.inversion.utils.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Test_PassMaker {

    @Test
    void testMaker()
    {
        final PasswordMaker pm = PasswordMaker.NEW();
        System.out.println( "pswd: " );
        System.out.println( pm.useRussian().make() );
        System.out.println( pm.useRussian(false).make() );
        System.out.println( pm.length(100).make() );
    }
}
