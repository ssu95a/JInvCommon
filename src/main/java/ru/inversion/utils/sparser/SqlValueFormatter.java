package ru.inversion.utils.sparser;

import ru.inversion.utils.converter.TypeConverter;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.Temporal;

/**
 * Форматирование Java-значения как SQL literal для diagnostic/legacy text APIs.
 * <p>
 * Не используется для JDBC execution: выполнение должно работать через bind parameters.
 */
public final class SqlValueFormatter {

   private SqlValueFormatter() {
   }

   public static String format( Object value ) {

        if( value == null )
            return " NULL ";

        Class<?> clazz = value.getClass();

        if( clazz == String.class )
            return quote((String)value);

        if( clazz == char[].class )
            return quote(new String((char[])value));

        if( clazz == Boolean.class )
            return ((Boolean)value) ? "1" : "0";

        if( clazz == java.sql.Date.class )
            return " to_date('" + formatDate((java.util.Date)value, "yyyy-MM-dd") + "','YYYY-MM-DD') ";

        if( clazz == LocalDate.class )
            return " to_date('" + formatDate(TypeConverter.convert(value, java.util.Date.class), "yyyy-MM-dd") + "','YYYY-MM-DD') ";

        if( value instanceof java.util.Date )
            return " to_timestamp( '" + formatDate((java.util.Date)value, "yyyy-MM-dd HH:mm:ss") + "','YYYY-MM-DD HH24:MI:SS') ";

        if( value instanceof Temporal )
            return " to_timestamp( '" + formatDate(TypeConverter.convert(value, java.util.Date.class), "yyyy-MM-dd HH:mm:ss") + "','YYYY-MM-DD HH24:MI:SS') ";

        return cleanupLegacyMarkers(value.toString());
    }

    private static String formatDate(java.util.Date value, String pattern) {
        // SimpleDateFormat is mutable; a fresh formatter keeps this static
        // utility safe for concurrent DataSets without ThreadLocal state.
        return new SimpleDateFormat(pattern).format(value);
    }

    private static String quote(String value) {
        return " '" + value.replace("'", "''") + "' ";
    }

    private static String cleanupLegacyMarkers( String value ) {
        return value.replace("/*ob#", "")
                .replace("#ob*/", "")
                .replace("~", "");
    }
}
