package ru.inversion.utils.sparser;

import ru.inversion.utils.converter.TypeConverter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.Temporal;

/**
 * Форматирование Java-значения как SQL literal для diagnostic/legacy text APIs.
 *
 * Не используется для JDBC execution: выполнение должно работать через bind parameters.
 */
public final class SqlValueFormatter {

    private SqlValueFormatter() {
    }

    public static String format( Object value ) {

        if( value == null )
            return " NULL ";

        Class clazz = value.getClass();

        if( clazz == String.class ) {
            String v = ((String)value).replace("'", "''");
            return " '" + v + "' ";
        }

        if( clazz == char[].class ) {
            String v = value.toString().replace("'", "''");
            return " '" + v + "' ";
        }

        if( clazz == Boolean.class )
            return ((Boolean)value) ? "1" : "0";

        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        DateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if( clazz == java.sql.Date.class )
            return " to_date('" + dateFormatter.format((java.sql.Date)value) + "','YYYY-MM-DD') ";

        if( clazz == LocalDate.class )
            return " to_date('" + dateFormatter.format(
                    TypeConverter.convert(value, java.util.Date.class)) + "','YYYY-MM-DD') ";

        if( value instanceof java.util.Date )
            return " to_timestamp( '" + timestampFormatter.format(
                    (java.util.Date)value) + "','YYYY-MM-DD HH24:MI:SS') ";

        if( value instanceof Temporal )
            return " to_timestamp( '" + timestampFormatter.format(
                    TypeConverter.convert(value, java.util.Date.class))
                    + "','YYYY-MM-DD HH24:MI:SS') ";

        return cleanupLegacyMarkers(value.toString());
    }

    private static String cleanupLegacyMarkers( String value ) {
        return value.replace("/*ob#", "")
                .replace("#ob*/", "")
                .replace("~", "");
    }
}
