package ru.inversion.utils.sparser;

import org.junit.Test;

import java.sql.Date;

import static org.junit.Assert.assertEquals;

public class SqlValueFormatterTest {

    @Test
    public void quotesTextAndEscapesApostrophes() {
        assertEquals(" 'O''Brien' ", SqlValueFormatter.format("O'Brien"));
    }

    @Test
    public void formatsNullAndBoolean() {
        assertEquals(" NULL ", SqlValueFormatter.format(null));
        assertEquals("1", SqlValueFormatter.format(Boolean.TRUE));
        assertEquals("0", SqlValueFormatter.format(Boolean.FALSE));
    }

    @Test
    public void formatsSqlDate() {
        assertEquals(
                " to_date('2026-10-26','YYYY-MM-DD') ",
                SqlValueFormatter.format(Date.valueOf("2026-10-26"))
        );
    }
}
