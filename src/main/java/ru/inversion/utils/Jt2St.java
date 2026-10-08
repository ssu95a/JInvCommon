package ru.inversion.utils;

import ru.inversion.utils.converter.TypeConverter;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 *
 * JavaType2SqlType
 * 
 * @author ssu
 */
public class Jt2St {
    
    /** */
    public static Class get( int type ) {
        
        switch( type ) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
                return String.class;
            case Types.NUMERIC:
            case Types.DECIMAL:
                return BigDecimal.class;
            case Types.BIT:
                return Boolean.class;
            case Types.TINYINT:
                return Byte.class;
            case Types.SMALLINT:
                return Short.class;
            case Types.INTEGER:
                return Integer.class;
            case Types.BIGINT:
                return Long.class;
            case Types.REAL:
            case Types.FLOAT:
                return Float.class;
            case Types.DOUBLE:
                return Double.class;
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return byte[].class;
            case Types.DATE:
                return java.sql.Date.class;
            case Types.TIME:
                return java.sql.Time.class;
            case Types.TIMESTAMP:
                return java.sql.Timestamp.class;
            case Types.CLOB:
                return Clob.class;
            case Types.BLOB:
                return Blob.class;
            case Types.ARRAY:
                return Array.class;
            case Types.JAVA_OBJECT:
                return Object.class;
        }

        return null;
        
        //throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "No conversion for SQLType " + type );
    }
    
    /** */
    public static int get( Class<?> clazz ) {
    
        if( clazz == null )
            return Types.NULL;

        if( clazz == Character.class )
            return Types.CHAR;
        if( clazz == String.class )
            return Types.VARCHAR;
        if( U.in(clazz, Boolean.class, boolean.class ) )

            return Types.INTEGER;
        if( clazz == Byte.class )
            return Types.TINYINT;
        if( U.in( clazz, Integer.class, Short.class ) )
            return Types.INTEGER;
        if( clazz == Long.class )
            return Types.BIGINT;
        if( clazz == BigDecimal.class )
            return Types.DECIMAL;
        if( U.in( clazz, Float.class, Double.class ) )
            return Types.DOUBLE;

        if( U.in( clazz, byte[].class, Byte[].class ) )
            return Types.VARBINARY;

        // Date
        if( clazz == java.sql.Date.class )
            return Types.DATE;
        if( clazz == LocalDate.class )
            return Types.DATE;
        if( clazz == java.util.Date.class )
            return Types.DATE;
        if( clazz == LocalDateTime.class )
            return Types.TIMESTAMP;

        if( clazz == java.sql.Time.class )
            return Types.TIME;
        if( clazz == LocalTime.class )
            return Types.TIME;
        if( clazz == java.sql.Timestamp.class )
            return Types.TIMESTAMP;

        if( clazz == Clob.class )
            return Types.CLOB;
        if( clazz == Blob.class )
            return Types.BLOB;

        if( clazz == Array.class )
            return Types.ARRAY;
        if( clazz == Struct.class )
            return Types.STRUCT;

        if( clazz == RowId.class )
            return Types.ROWID;

        if( clazz == Object.class )
            return Types.JAVA_OBJECT;
        
        if( InputStream.class.isAssignableFrom(clazz) )
            return Types.VARBINARY;

        if( SQLData.class.isAssignableFrom(clazz) )
            return Types.STRUCT;

        if( ResultSet.class.isAssignableFrom(clazz) )
            return Types.REF_CURSOR;

        throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "No conversion in SQLType for " + clazz.getName() );
    }

    /** Преобразование из типов-значений Java -> SQL*/
    public static Object convert( Object value ) {
        
        if( value == null )
            return null;

        if( value instanceof Long )
        {
            long v = ((Long)value);
            if( v > Integer.MAX_VALUE || v < Integer.MIN_VALUE )
                return v;

            return ((Long)value).intValue();
        }

        Class<?> clazz = value.getClass( );
    
        if( clazz == Boolean.class )
            return TypeConverter.convert( value, Integer.class );
        
        if( clazz == LocalDate.class )
            return TypeConverter.convert( value, java.sql.Date.class );

        if( U.in( clazz, LocalDateTime.class, LocalTime.class ) )
            return TypeConverter.convert( value, java.sql.Timestamp.class );

        // Пустые строки заменяем на null
        if( clazz == String.class ) {
            if( ((String)value).isEmpty() )
                return null;
        }

        // Один символ на строку
        if( clazz == Character.class )
            return TypeConverter.convert( value, String.class );

        return value;
    }

    /** */
    public static Object r_convert( Object value ) {
        
        if( value == null )
            return null;

        final Class<?> clazz = value.getClass( );
        
        if( clazz == java.sql.Date.class )
            return TypeConverter.convert( value, LocalDate.class );
        
        if( clazz == java.sql.Timestamp.class )
            return TypeConverter.convert( value,  LocalDateTime.class );
        
        return value;
    }

    /** */
    public static int typeOf( String name )
    {
        if( name == null )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'name' is null" );

        if( "JSON_STRUCT".equalsIgnoreCase(name) )
            return -1024;
        if( "JSON_ARRAY".equalsIgnoreCase(name) )
            return -1025;
        if( "IN_LIST".equalsIgnoreCase(name) )
            return -1026;

        return JDBCType.valueOf(name).getVendorTypeNumber();

    }
}
