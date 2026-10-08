package ru.inversion.utils.converter;

import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.io.RawBAOS;
import ru.inversion.utils.io.RawCAW;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;
import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Date;
import java.util.*;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

/**
 * in development
 *
 * @author ssu @
 */
public class TypeConverter {

    static private class PairClass {

        private final Class c1, c2;
        private final int hash;

        public PairClass(Class c1, Class c2) {
            this.c1 = c1;
            this.c2 = c2;
            this.hash = Objects.hash(c1,c2);
        }

        @Override
        public boolean equals(Object obj) {
            PairClass p = (PairClass) obj;
            return this.c1 == p.c1 && this.c2 == p.c2;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
    /** */
    static private class PairStringClass<T> {

        private final String s;
        private final Class<T>  c;
        private final int hash;

        public PairStringClass( String s, Class<T> c ) {
            this.s = s;
            this.c = c;
            this.hash = Objects.hash(c,s);
        }

        @Override
        public boolean equals(Object obj) {
            PairStringClass p = (PairStringClass) obj;
            return this.c == p.c && this.s.equals(p.s);
        }

        @Override
        public int hashCode() {
            return hash;
        }

    }

    // Boolean 
    private static final IConverter Long_to_Bool = new IConverter<Long, Boolean>() {
        @Override
        public Long from(Boolean value) {
            return value == null ? null : (value ? 1L : 0L);
        }

        @Override
        public Boolean to(Long value) {
            return value == null ? null : (value == 0L ? Boolean.FALSE : Boolean.TRUE);
        }
    };

    private static final IConverter Byte_to_Bool = new IConverter<Byte, Boolean>() {
        @Override
        public Byte from(Boolean value) {
            return value == null ? null : (value ? (byte)1 : (byte)0);
        }

        @Override
        public Boolean to(Byte value) {
            return value == null ? null : (value == 0 ? Boolean.FALSE : Boolean.TRUE);
        }
    };

    private static final IConverter Int_to_Bool = new IConverter<Integer, Boolean>() {
        @Override
        public Integer from(Boolean value) {
            return value == null ? null : (value ? 1 : 0);
        }

        @Override
        public Boolean to(Integer value) {
            return value == null ? null : (value == 0 ? Boolean.FALSE : Boolean.TRUE);
        }
    };

    private static final IConverter Dec_to_Bool = new IConverter<BigDecimal, Boolean>() {
        @Override
        public BigDecimal from(Boolean value) {
            return value == null ? null : (value ? BigDecimal.ONE : BigDecimal.ZERO);
        }

        @Override
        public Boolean to(BigDecimal value) {
            return value == null ? null : (value.equals(BigDecimal.ZERO) ? Boolean.FALSE : Boolean.TRUE);
        }
    };

    private static final IConverter Dec_to_BigInt = new IConverter<BigDecimal, BigInteger>() {
        @Override
        public BigInteger to( BigDecimal value ) {
            return value == null ? null : value.toBigInteger();
        }

        @Override
        public BigDecimal from( BigInteger value ) {
            return value == null ? null : new BigDecimal(value);
        }
    };

    private static final IConverter Str_to_Bool = new IConverter<String, Boolean>() {
        @Override
        public String from(Boolean value) {
            return value == null ? null : value.toString();
        }

        @Override
        public Boolean to(String value) {
            return value == null ? null : S.inIgnoreCase( value, "1", "true", "on", "yes", "y", "да", "t" );
                    //Boolean.valueOf(value);
        }
    };

    private static final IConverter Int_to_Byte = new IConverter<Integer, Byte>() {

        @Override
        public Byte to(Integer value) {
            return value == null ? null : value.byteValue();
        }

        @Override
        public Integer from(Byte value) {
            return value == null ? null : value.intValue();
        }
    };

    private static final IConverter Int_to_Long = new IConverter<Integer, Long>() {

        @Override
        public Long to(Integer value) {
            return value == null ? null : value.longValue();
        }

        @Override
        public Integer from(Long value) {
            return value == null ? null : value.intValue();
        }
    };

    //BigDecimal
    private static final IConverter Dec_to_Long = new IConverter<BigDecimal, Long>() {

        @Override
        public Long to(BigDecimal value) {
            return value == null ? null : value.longValue();
        }

        @Override
        public BigDecimal from(Long value) {
            return value == null ? null : BigDecimal.valueOf(value);
        }
    };

    private static final IConverter Dec_to_Int = new IConverter<BigDecimal, Integer>() {

        @Override
        public Integer to(BigDecimal value) {
            return value == null ? null : value.intValue();
        }

        @Override
        public BigDecimal from(Integer value) {
            return value == null ? null : BigDecimal.valueOf(value);
        }
    };

    //Character
    private static final IConverter Str_to_Char = new IConverter<String, Character>() {
        @Override
        public Character to( String value ) {

            try {

                if( value == null )
                    return null;

                switch( value.length() ) {
                    case 0:
                        return (char)0;
                    case 1:
                        return value.charAt(0);
                }//
                throw new IllegalArgumentException( "value - '" + value + "' - length greater than 1 character" );
            }
            catch( Exception ex ) {
                TypeConverterException.fromValueToClass( value, Character.class, ex, "Str_to_Char" );
            }

            return (char)0; //stub
        }
        @Override
        public String from( Character value ) {
            return value == null ? null : value.toString();
        }
    };

    // Маска для преобразования чисел для простых случаев
    static private final Pattern simpleIntPattern = Pattern.compile("^\\d+$");

    // Number
    private static final IConverter<String, Number> Str_to_Num = new IConverter<String, Number>() {
        @Override
        public Number to( String value ) {

            try {

                if( S.isNullOrEmpty(value) )
                    return null;

                value = value.trim();
                if( value.isEmpty() )
                    return null;

                if( !simpleIntPattern.matcher(value).matches() )
                {
                    boolean hasDot = false;
                    char ch;
                    final StringBuilder sb = new StringBuilder();

                    for(int i = 0; i < value.length(); i++) {

                        ch = value.charAt(i);

                        if( Character.isSpaceChar(ch) )
                            continue;

                        if( ch == '+' || ch == '-' ) {

                            if(i != 0)
                                throw new NumberFormatException("Bad string '" + value + "' for convert to Number");

                            sb.append(ch);

                            continue;
                        }

                        if(ch == '.' || ch == ',') {
                            if(hasDot)
                                throw new NumberFormatException("Bad string '" + value + "' for convert to Number");

                            hasDot = true;

                            if(i == 0 || (i == 1 && U.inChar(sb.charAt(0), '+', '-')))
                                sb.append('0');

                            sb.append('.');

                            continue;
                        }

                        if( Character.isDigit(ch) )
                            sb.append(ch);
                        else
                            throw new NumberFormatException("Bad string '" + value + "' for convert to Number");
                    }//end for

                    if( hasDot )
                        return new BigDecimal(sb.toString());

                    value = sb.toString();
                }

                if( value.length() <= 9 )
                    return Integer.parseInt(value);

                if( value.length() <= 18 )
                    return Long.parseLong( value);

                return new BigInteger( value );

            } catch( Throwable th ) {
                TypeConverterException.fromValueToClass( value, Number.class, th, "Str_to_Num");
            }

            return null; //stub
        }

        @Override
        public String from( Number value ) {
            return value == null ? null : value.toString();
        }
    };

    // Date zone //

    /** */
    private static final IConverter<java.util.Date, LocalDate> UtilDate_to_LocalDate = new IConverter<java.util.Date, LocalDate>() {
        @Override
        public LocalDate to(Date value) {
            return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        @Override
        public Date from(LocalDate value) {
            return Date.from(value.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        }
    };

    /** */
    private static final IConverter UtilDate_to_LocalDateTime = new IConverter<java.util.Date, LocalDateTime>() {
        @Override
        public LocalDateTime to(Date value) {
            return LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
        }

        @Override
        public Date from(LocalDateTime value) {
            return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
        }
    };

    /** */
    private static final IConverter UtilDate_to_LocalTime = new IConverter<java.util.Date, LocalTime>() {
        @Override
        public LocalTime to(Date value) {
            return LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault()).toLocalTime();
        }

        @Override
        public Date from(LocalTime value) {
            //TypeConverterException.fromValueToClass( value.getClass(), java.util.Date.class, new IllegalStateException("Для преобразования из времении в дату, не определена базовая дата, в какую присвоить время"), this );
            return Date.from(value.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant());
        }
    };

    /** */
    static final IConverter SqlDate_to_UtilDate = new IConverter< java.sql.Date, java.util.Date>() {

        @Override
        public java.util.Date to(java.sql.Date value) {
            return new java.util.Date(value.getTime());
        }

        @Override
        public java.sql.Date from(java.util.Date value) {
            return new java.sql.Date(value.getTime());
        }
    };

    /** */
    private static final IConverter SqlDate_to_LocalDate = new IConverter<java.sql.Date, LocalDate>() {

        @Override
        public LocalDate to(java.sql.Date value) {
            return U.callIfNotNull(value, java.sql.Date::toLocalDate);
        }

        @Override
        public java.sql.Date from(LocalDate value) {
            //return U.callIfNotNull( value, (v)->new java.sql.Date( Date.from(v.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()).getTime() ) );
            return U.callIfNotNull(value, java.sql.Date::valueOf );
        }
    };

    /** */
    private static final IConverter SqlDate_to_LocalDateTime = new IConverter<java.sql.Date, LocalDateTime>() {

        @Override
        public LocalDateTime to(java.sql.Date value) {
            return value.toLocalDate().atStartOfDay();
        }

        @Override
        public java.sql.Date from(LocalDateTime value) {
            return java.sql.Date.valueOf( value.toLocalDate() );
        }
    };

    /** */
    private static final IConverter SqlDate_to_Timestamp = new IConverter<java.sql.Date, Timestamp>() {

        @Override
        public Timestamp to(java.sql.Date value) {
            return Timestamp.from(value.toInstant());
        }

        @Override
        public java.sql.Date from(Timestamp value) {
            return new java.sql.Date(value.getTime());
        }
    };

    /** */
    private static final IConverter SqlTime_to_LocalTime = new IConverter<java.sql.Time, LocalTime>() {

        @Override
        public LocalTime to(java.sql.Time value) {
            return U.callIfNotNull(value, java.sql.Time::toLocalTime);
        }

        @Override
        public java.sql.Time from(LocalTime value) {
            return U.callIfNotNull(value, java.sql.Time::valueOf );
        }
    };

    /** */
    private static final IConverter Timestamp_to_LocalDateTime = new IConverter<Timestamp, LocalDateTime>() {
        @Override
        public LocalDateTime to(Timestamp value) {
            return value.toLocalDateTime();//LocalDateTime.ofInstant( value.toInstant(), ZoneId.systemDefault() );
        }

        @Override
        public Timestamp from(LocalDateTime value) {
            return Timestamp.valueOf(value);
        }
    };

    /** */
    private static final IConverter Timestamp_to_LocalDate = new IConverter<Timestamp, LocalDate>() {
        @Override
        public LocalDate to(Timestamp value) {
            return value.toLocalDateTime().toLocalDate();
        }

        @Override
        public Timestamp from(LocalDate value) {
            return Timestamp.valueOf(value.atStartOfDay());
        }
    };

    /** */
    private static final IConverter Timestamp_to_LocalTime = new IConverter<Timestamp, LocalTime>() {
        @Override
        public LocalTime to(Timestamp value) {
            return value.toLocalDateTime().toLocalTime();
        }

        @Override
        public Timestamp from(LocalTime value) {
            return Timestamp.valueOf(value.atDate(LocalDate.now()));
        }
    };
    
    /** */
    private static final IConverter Timestamp_to_Long = new IConverter<Timestamp, Long>() {
        @Override
        public Long to(Timestamp value) {
            return value.getTime();
        }

        @Override
        public Timestamp from(Long value) {
            return new Timestamp(value);
        }
    };

    private static final IConverter Timestamp_to_Int = new IConverter<Timestamp, Integer>() {
        @Override
        public Integer to(Timestamp value) {
            return (int)value.getTime();
        }

        @Override
        public Timestamp from(Integer value) {
            return new Timestamp( value.longValue() );
        }
    };

    private static final IConverter LocalDate_to_LocalDateTime = new IConverter<LocalDate, LocalDateTime>() {
        @Override
        public LocalDateTime to( LocalDate value ) {
            return U.callIfNotNull( value, LocalDate::atStartOfDay );
        }
        @Override
        public LocalDate from( LocalDateTime value ) {
            return value == null ? null : value.toLocalDate();
        }
    };

    /** */
    private static final IConverter Int_to_String = new IConverter<Integer, String>() {

        @Override
        public String to(Integer value) {
            return value == null ? null : Integer.toString( value );
        }

        @Override
        public Integer from(String value) {
            return S.isNullOrEmpty(value) ? null : Integer.valueOf( value );
        }
    };

    /** */
    private static final IConverter Long_to_String = new IConverter<Long, String>() {

        @Override
        public String to(Long value) {
            return value == null ? null : Long.toString( value );
        }

        @Override
        public Long from(String value) {
            try {
                return S.isNullOrEmpty(value) ? null : Long.valueOf( value );
            }
            catch( Exception ex ) {
                TypeConverterException.fromValueToClass( value, Long.class, ex, "Long_to_String" );
            }
            return 0L;
        }
    };

    /** */
    private static final IConverter Clob_to_String = new IConverter<Clob, String>() {

        @Override
        public String to(Clob value) {

            String result = null;

            try {

                long clobLength = value.length();

                if( clobLength > Integer.MAX_VALUE )
                    throw new IllegalStateException( "CLOB size too big for String! clobLength = " + clobLength );

                result = value.getSubString(1, (int) clobLength );

            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( value.getClass(), String.class, th, "Clob_to_String" );
            }
//            finally {
//                try {
//                    value.free();
//                } catch (SQLException ex) {
//                    ex.printStackTrace();
//                }
//            }
            return result;
        }

        /**
         *
         */
        @Override
        public Clob from( String value ) {
            try {
                return new SerialClob( value.toCharArray() );
            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( value.getClass(), Clob.class, th, "Clob_to_String");
            }
            return null;
        }
    };

    /** */
    private static final IConverter Blob_to_String = new IConverter<Blob, String>() {

        @Override
        public String to( Blob value ) {

            String result = null;

            try {

                final long blobLength = value.length();

                if( blobLength > Integer.MAX_VALUE )
                    throw new IllegalStateException( "BLOB size too big for String! blobLength = " + blobLength );

                ByteArrayOutputStream baos = new ByteArrayOutputStream( (int)blobLength );
                InputStream           is   = value.getBinaryStream();

                byte[] buffer = new byte[1024];
                int length;

                while( (length = is.read(buffer)) != -1 ) {
                    baos.write(buffer, 0, length);
                }

                return baos.toString( UTF_8.name() );

            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( value.getClass(), String.class, th, "Blob_to_String");
            }
//            finally {
//                try {
//                    value.free();
//                } catch (SQLException ex) {
//                    ex.printStackTrace();
//                }
//            }
            return result;
        }

        /** */
        @Override
        public Blob from( String value ) {
            try {
                return new SerialBlob( value.getBytes() );
            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( value.getClass(), Blob.class, th, "Blob_to_String");
            }
            return null;
        }
    };

    /** */
    private static final IConverter Blob_to_Bytea = new IConverter<Blob, byte[]>() {

        @Override
        public byte[] to( Blob value ) {
            try {
                return value.getBytes(1, (int)value.length() );
            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( value.getClass(), Blob.class, th, "Blob_to_Bytea");
            }
            return null;
        }

        @Override
        public Blob from( byte[] value ) {
            try {
                return value == null ? null : new SerialBlob(value);
            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( value.getClass(), Blob.class, th, "Blob_to_Bytea");
            }
            return null;
        }
    };

    /** */
    private static final IConverter RowId_to_String = new IConverter<RowId, String>() {
        @Override
        public String to(RowId value) {
            return value.toString();
        }
        /** */
        @Override
        public RowId from(String value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };

    /** */
    private static final IConverter Reader_to_String = new IConverter< Reader, String>() {

        @Override
        public String to( Reader r ) {

            if( r == null )
                return null;

            return S.toString(r);
        }

        @Override
        public Reader from( String value ) {
            return value == null ? null :  new StringReader(value);
        }

    };

    /** */
    private static final IConverter Reader_to_Chars = new IConverter<Reader,char[]>() {

        @Override
        public char[] to( Reader r ) {

            if( r == null )
                return null;

            try {
                /*
                char[] buffer = new char[1024];

                int n;

                CharArrayWriter caw = new CharArrayWriter();

                while( ( n = r.read(buffer) ) != -1)
                         caw.write( buffer, 0, n );

                return caw.toCharArray();
                */

                final RawCAW rc = new RawCAW();
                rc.write(r);
                return rc.buf();

            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( r.getClass(), char[].class, th, "Reader_to_Chars" );
            }

            return null;
        }

        @Override
        public Reader from( char[] value ) {
            return new CharArrayReader(value);
        }
    };

    /** */
    private static final IConverter InputStream_to_Bytes = new IConverter< InputStream, byte[]>() {

        @Override
        public byte[] to( InputStream is ) {

            if( is == null )
                return null;

            try {

                /*
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                byte[] buffer = new byte[1024];

                int n;

                while( ( n = is.read(buffer) ) != -1)
                         baos.write( buffer, 0, n );

                return baos.toByteArray();
                */

                final RawBAOS rb = new RawBAOS();
                rb.write(is);
                return rb.buf();

            } catch (Throwable th) {
                TypeConverterException.fromClassToClass( is.getClass(), byte[].class, th, "InputStream_to_Bytes" );
            }

            return null;

        }

        @Override
        public InputStream from( byte[] bytes ) {
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }
    };

    /** */
    private static final IConverter<File,String> File_to_String = new IConverter<File,String>() {

        @Override
        public String to( File file ) {
            return file == null ? null : file.getAbsolutePath();
        }
        @Override
        public File from( String s ) {
            return s == null ? null : new File(s);
        }
    };

    /** */
    private static final IConverter<char[],byte[]> Chara_to_Bytea = new IConverter<char[],byte[]>() {

        @Override
        public byte[] to( char[] v ) {
            //return UTF_8.encode(CharBuffer.wrap(v)).array();
            return new String(v).getBytes(StandardCharsets.UTF_8);
        }
        @Override
        public char[] from( byte[] v ) {
            //return UTF_8.decode(ByteBuffer.wrap(v)).array();
            return new String(v, StandardCharsets.UTF_8).toCharArray();
        }
    };

    private static final IConverter<char[],String> Chara_to_String = new IConverter<char[],String>() {

        @Override
        public String to( char[] v ) {
            if( v == null )
                return null;
            if( v.length == 0 )
                return S.EMPTY_STRING;
            return String.valueOf(v);
        }
        @Override
        public char[] from( String v ) {
            if( v == null )
                return null;
            if( v.isEmpty() )
                return new char[0];

            return v.toCharArray();
        }
    };

    private static final IConverter<byte[],String> Bytea_to_String = new IConverter<byte[],String>() {

        @Override
        public String to( byte[] v ) {
            if( v == null )
                return null;
            if( v.length == 0 )
                return S.EMPTY_STRING;

            return new String(v,UTF_8);
        }
        @Override
        public byte[] from( String v ) {
            if( v == null )
                return null;
            if( v.isEmpty() )
                return new byte[0];

            return v.getBytes(UTF_8);
        }
    };

    /** */
    private static final IFormatConverter BigDecimal_to_String = new IFormatConverter<BigDecimal>() {
    
        @Override
        public String to( BigDecimal value, String formatMask ) {
            return new DecimalFormat(formatMask).format(value);
        }

        @Override
        public BigDecimal from( String value, String formatMask ) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String to( BigDecimal value ) {
            return U.callIfNotNull(value, BigDecimal::toString);
        }

        @Override
        public BigDecimal from( String value ) {
            try {
                return S.isNullOrEmpty(value) ? null : new BigDecimal(value);
            } catch (Throwable th) {
                TypeConverterException.fromValueToClass( value, BigDecimal.class, th, "BigDecimal_to_String" );
            }
            return null;
        }
    };

    private static final IFormatConverter<java.sql.Date> SqlDate_to_String = new IFormatConverter<java.sql.Date>() {

        final private DateTimeFormatter f  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final private DateTimeFormatter f2 = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        @Override
        public String to(java.sql.Date value) {
            return value.toLocalDate().format(f);
        }
        /** */
        @Override
        public java.sql.Date from(String s) {

            try {

                if( S.isNullOrEmpty(s) )
                    return null;

                if( "dd.MM.yyyy".length() == s.length() ) {

                    boolean doIt = false;

                    for( int i = 0; i < s.length(); i++ ) {

                        switch(i) {
                            case 2: case 5:
                                doIt = s.charAt(i) == '.';
                            break;
                            default:
                                doIt = Character.isDigit( s.charAt(0) );
                        }

                        if( !doIt )
                            break;
                    }

                    if( doIt ) {
                        LocalDate ld = LocalDate.parse(s,f2);
                        return java.sql.Date.valueOf( ld );
                    }
                }

                if( s.length() > "yyyy-mm-dd".length() )
                {
                    Timestamp ts = Timestamp.valueOf(s);
                    return new java.sql.Date(ts.getTime());
                } 
                else 
                {
                    return java.sql.Date.valueOf(s);
                    //return new java.sql.Date(g_formatSQLDate.parse(s).getTime());
                }
            } catch (Throwable ex) {
                TypeConverterException.fromValueToClass(s, java.sql.Date.class, ex, "SqlDate_to_String" );
            }
            return null;
        }
        /** */
        @Override
        public String to(java.sql.Date value, String mask) {
            return new SimpleDateFormat(mask).format(value);
        }
        /** */
        @Override
        public java.sql.Date from(String value, String formatMask) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    };

    private static final IFormatConverter<java.util.Date> UtilDate_to_String = new IFormatConverter<java.util.Date>() {

        final private SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

        @Override
        public String to(java.util.Date value) {
            return f.format(value);
        }
        /** */
        @Override
        public java.util.Date from(String s) {

            try {
                if( s != null )
                    return f.parse(s);
            } catch( Exception ex) {
                TypeConverterException.fromValueToClass(s, java.sql.Date.class, ex, "UtilDate_to_String" );
            }
            return null;
        }
        /** */
        @Override
        public String to(java.util.Date value, String mask) {
            return new SimpleDateFormat(mask).format(value);
        }
        /** */
        @Override
        public java.sql.Date from( String value, String formatMask ) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    };
    
    
    /** */
    private static final IFormatConverter LocalDate_to_String = new IFormatConverter<LocalDate>() {

        private final DateTimeFormatter f1 = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        private final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("dd.MM.yy"  );
        private final DateTimeFormatter f3 = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yy").toFormatter(Locale.US);
        //private final Pattern f3 = Pattern.compile("^\\d{2}-[A-Z]{3}-\\d{2}$");

        @Override
        public String to( LocalDate value ) {
            return U.callIfNotNull( value, f1::format );
        }
        /** */
        @Override
        public LocalDate from( String value ) {
            
            try {
                
                if( !S.isNullOrEmpty(value) ) {

                    return LocalDate.parse( value,
                        U.decode(
                            value.length(),
                            8,  f2,
                            10, ( value.indexOf('-') == -1 ? f1 : ISO_LOCAL_DATE ),
                            9,  f3,
                            ISO_LOCAL_DATE
                        )
                    );
                }
                
            } catch( Exception ex ) {
                TypeConverterException.fromValueToClass( value, LocalDate.class, ex, "LocalDate_to_String" );
            }
            return null;
        }
        /** */
        @Override
        public String to( LocalDate value, String mask ) {
            mask = mask.replaceAll("[H:m]", "");
            return DateTimeFormatter.ofPattern(mask).format(value);
        }
        /** */
        @Override
        public LocalDate from( String value, String formatMask ) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };



    /** */
    private static final IFormatConverter LocalDateTime_to_String = new IFormatConverter<LocalDateTime>() {

        final private DateTimeFormatter f  = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        final private DateTimeFormatter f2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        /** */
        @Override
        public String to(LocalDateTime value) {
            return U.callIfNotNull( value, f::format );
        }

        /** */
        @Override
        public LocalDateTime from(String value) {

            try {

                if( !S.isNullOrEmpty(value))
                {
                    if( value.length() == 19 )
                    {
                        if( value.charAt(10) == 'T' )
                            return LocalDateTime.parse( value, DateTimeFormatter.ISO_LOCAL_DATE_TIME );

                        if( value.charAt(2) == '.' )
                            return LocalDateTime.parse( value, f );
                        else
                            return LocalDateTime.parse( value, f2);
                    }

                    return LocalDateTime.parse(value);
                }
            } catch (Exception ex) {
                TypeConverterException.fromValueToClass(value, LocalDateTime.class, ex, "LocalDateTime_to_String");
            }
            return null;
        }

        /** */
        @Override
        public String to(LocalDateTime value, String mask) {
            return DateTimeFormatter.ofPattern(mask).format(value);
        }

        @Override
        public LocalDateTime from(String value, String formatMask) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(formatMask));
            } catch (Exception ex) {
                TypeConverterException.fromValueToClass(value, LocalDateTime.class, ex, "LocalDateTime_to_String");
            }
            return null;
        }
    };

        /** */
    private static final IFormatConverter LocalTime_to_String = new IFormatConverter<LocalTime>() {

        final private DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss");

        /** */
        @Override
        public String to(LocalTime value) {
            return U.callIfNotNull( value, f::format );
        }

        /** */
        @Override
        public LocalTime from(String value) {
            try {
                if( value != null )
                    return LocalTime.parse( value, f );
            } catch (Exception ex) {
                TypeConverterException.fromValueToClass(value, LocalTime.class, ex, "LocalTime_to_String");
            }
            return null;
        }

        /** */
        @Override
        public String to(LocalTime value, String mask) {
            return DateTimeFormatter.ofPattern(mask).format(value);
        }

        @Override
        public LocalTime from(String value, String formatMask) {
            try {
                return LocalTime.parse(value, DateTimeFormatter.ofPattern(formatMask));
            } catch (Exception ex) {
                TypeConverterException.fromValueToClass(value, LocalTime.class, ex, "LocalTime_to_String");
            }
            return null;
        }
    };
    
    private static final IFormatConverter Timestamp_to_String = new IFormatConverter<Timestamp>() {

        //final private SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        final private DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        /** */
        @Override
        public String to(Timestamp value, String formatMask) {
            //return new SimpleDateFormat(formatMask).format(value);
            return value == null ? null :
                    value.toLocalDateTime()
                            .format(DateTimeFormatter.ofPattern(formatMask));
        }
        /** */
        @Override
        public Timestamp from(String value, String formatMask) {
            try {
                if( S.isNullOrEmpty(value))
                    return null;

                LocalDateTime localDateTime = LocalDateTime.parse(value, DateTimeFormatter.ofPattern(formatMask));
                return Timestamp.valueOf(localDateTime);
            } catch (Exception ex) {
                TypeConverterException.fromValueToClass(value, Timestamp.class, ex, "Timestamp_to_String with mask");
            }
            return null;
        }

        /** */
        @Override
        public String to(Timestamp value) {
            return value == null ? null : value.toLocalDateTime().format(f);
        }
        /** */
        @Override
        public Timestamp from(String value) {
            try {

                if( S.isNullOrEmpty(value) )
                    return null;
                LocalDateTime localDateTime = LocalDateTime.parse(value, f);
                return Timestamp.valueOf(localDateTime);
            } catch (Exception ex) {
                TypeConverterException.fromValueToClass(value, Timestamp.class, ex, "Timestamp_to_String");
            }
            return null;
        }
    };


    //
    private static final Map<PairClass, IConverter> g_converterMap;
    private static final Map<Class,     IFormatConverter> g_formatConverterMap;
    private static final Map< PairStringClass, IConverter> g_structConverterMap;

    static {

        g_converterMap = new HashMap<>();
//+----------------------------------------------------------------------------------------------------+        
//|                       |  class from            |        class to     |             impl            | 
//+----------------------------------------------------------------------------------------------------+          
        registerConverter( Long.class,              Boolean.class,         Long_to_Bool              );
        registerConverter( Integer.class,           Boolean.class,         Int_to_Bool               );
        registerConverter( BigDecimal.class,        Boolean.class,         Dec_to_Bool               );
        registerConverter( String.class,            Boolean.class,         Str_to_Bool               );
        registerConverter( Integer.class,           Long.class,            Int_to_Long               );
        registerConverter( Integer.class,           Byte.class,            Int_to_Byte               );
        registerConverter( BigDecimal.class,        Long.class,            Dec_to_Long               );
        registerConverter( BigDecimal.class,        Integer.class,         Dec_to_Int                );
        registerConverter( String.class,            Character.class,       Str_to_Char               );
        registerConverter( String.class,            Number.class,          Str_to_Num                );
        registerConverter( BigDecimal.class,        BigInteger.class,      Dec_to_BigInt             );

        registerConverter( java.util.Date.class,    LocalDate.class,       UtilDate_to_LocalDate     );
        registerConverter( java.util.Date.class,    LocalDateTime.class,   UtilDate_to_LocalDateTime );
        registerConverter( java.util.Date.class,    LocalTime.class,       UtilDate_to_LocalTime     );
        registerConverter( java.util.Date.class,    String.class,          UtilDate_to_String        );

        registerConverter( java.sql.Date.class,     LocalDate.class,       SqlDate_to_LocalDate      );
        registerConverter( java.sql.Date.class,     String.class,          SqlDate_to_String         );
        registerConverter( java.sql.Date.class,     java.util.Date.class,  SqlDate_to_UtilDate       );
        registerConverter( java.sql.Date.class,     Timestamp.class,       SqlDate_to_Timestamp      );
        registerConverter( java.sql.Date.class,     LocalDateTime.class,   SqlDate_to_LocalDateTime  );

        registerConverter( Timestamp.class,         LocalDate.class,       Timestamp_to_LocalDate    );
        registerConverter( Timestamp.class,         LocalDateTime.class,   Timestamp_to_LocalDateTime);
        registerConverter( Timestamp.class,         LocalTime.class,       Timestamp_to_LocalTime    );
        registerConverter( Timestamp.class,         String.class,          Timestamp_to_String       );
        registerConverter( Timestamp.class,         Long.class,            Timestamp_to_Long         );
        registerConverter( Timestamp.class,         Integer.class,         Timestamp_to_Int          );

        registerConverter( java.sql.Time.class,     LocalTime.class,       SqlTime_to_LocalTime      );

        registerConverter( LocalDate.class,         String.class,          LocalDate_to_String       );
        registerConverter( LocalTime.class,         String.class,          LocalTime_to_String       );
        registerConverter( LocalDateTime.class,     String.class,          LocalDateTime_to_String   );
        registerConverter( Integer.class,           String.class,          Int_to_String             );
        registerConverter( BigDecimal.class,        String.class,          BigDecimal_to_String      );
        registerConverter( Long.class,              String.class,          Long_to_String            );
        registerConverter( RowId.class,             String.class,          RowId_to_String           );

        registerConverter( LocalDate.class,         LocalDateTime.class,   LocalDate_to_LocalDateTime);
        registerConverter( File.class,              String.class,          File_to_String            );
        registerConverter( char[].class,            byte[].class,          Chara_to_Bytea            );
        registerConverter( char[].class,            String.class,          Chara_to_String           );
        registerConverter( byte[].class,            String.class,          Bytea_to_String           );

        g_formatConverterMap = new HashMap<>();
        g_formatConverterMap.put( java.sql.Date.class,  SqlDate_to_String       );
        g_formatConverterMap.put( BigDecimal.class,     BigDecimal_to_String    );
        g_formatConverterMap.put( LocalDateTime.class,  LocalDateTime_to_String );
        g_formatConverterMap.put( LocalDate.class,      LocalDate_to_String     );
        g_formatConverterMap.put( LocalTime.class,      LocalTime_to_String     );
        g_formatConverterMap.put( Timestamp.class,      Timestamp_to_String     );
        g_formatConverterMap.put( java.util.Date.class, UtilDate_to_String      );

        g_structConverterMap = new HashMap<>();
    }

    /**
     *
     */
    private static class ReverseConverter<T,S> implements IConverter<T,S> {

        private final IConverter<S,T> converter;

        public ReverseConverter( IConverter<S,T> converter) {
            this.converter = converter;
        }

        @Override
        public S to(T value) {    
            return converter.from(value);
        }

        @Override
        public T from(S value) {
            return converter.to(value);
        }
    }

    /** */
    private static <E extends Enum<E>> E stringToEnum( Class clazz, String name ) {

        if( S.isNullOrEmpty(name) )
            return null;

        try {

            Class<E> enumClass = clazz;

            return Enum.valueOf( enumClass, name );
        }
        catch( Throwable th ) {
            TypeConverterException.fromValueToClass(name, clazz, th, "stringToEnum");
        }
        return null;
    }

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert( Object value, Class<T> toClass ) {

        if( value == null || toClass == null )
            return null;

        if( value.getClass() == toClass || toClass == Object.class )
            return (T)value;

        IConverter<Object,T> converter = getConverter( value.getClass(), toClass );

        if( converter != null )
            return converter.to(value);

        if( toClass.isEnum() )
            return (T)stringToEnum( toClass, value.toString() );

        if( toClass == String.class )
        {
            if( value.getClass().isEnum() )
                return (T)((Enum)value).name();

            return (T)value.toString();
        }
        // Struct
        if( value instanceof Struct )
        {
            final Struct struct = (Struct)value;

            try {
                final IConverter<T, Struct> sc = g_structConverterMap.get( new PairStringClass(struct.getSQLTypeName(), toClass) );
                if( sc != null )
                    return sc.from( struct );
            }
            catch( SQLException ex ) {
                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on get 'getSQLTypeName' from STRUCT", ex );
            }
        }

        return toClass.cast(value); //do throw
    }

    /*
     *
     */
    public static String convertToString( Object value, String formatMask ) {

        if (value == null) {
            return null;
        }

        if( value.getClass() == String.class )
            return (String)value;

        if (formatMask == null) {
            return convert(value, String.class);
        }

        IFormatConverter<Object> fc = g_formatConverterMap.get( value.getClass() );
        if (fc != null) {
            return fc.to(value, formatMask);
        }

        TypeConverterException.fromValueToClass( value, String.class, null, fc == null ? "<null>" : fc.getClass().getName()  );

        return null; //stub
    }

    /**
     *
     */
    public static String convertToString( Object value, String formatMask, Class byClass) {

        if (value == null) {
            return null;
        }

        if( value.getClass() == String.class )
            return (String)value;

        if (byClass == null) {
            return convertToString(value, formatMask);
        }

        if (formatMask == null) {
            return convert( value, String.class );
        }

        return convertToString( convert( value, byClass), formatMask);
    }

    /** */
    private static <T> IConverter<Object,T> getInterfaceConverter( Class fromClass, Class toClass ) {

        if( RowId.class.isAssignableFrom(fromClass) && String.class == toClass) {
            return RowId_to_String;
        }

        if( Clob.class.isAssignableFrom(fromClass) && String.class == toClass ) {
            return Clob_to_String;
        }
        if( Clob.class.isAssignableFrom(toClass) && String.class == fromClass ) {
            return new ReverseConverter<>( Clob_to_String );
        }

        if( Blob.class.isAssignableFrom(fromClass) && String.class == toClass ) {
            return Blob_to_String;
        }
        if( Blob.class.isAssignableFrom(toClass) && String.class == fromClass ) {
            return new ReverseConverter<>( Blob_to_String );
        }

        if( Blob.class.isAssignableFrom(fromClass) && byte[].class == toClass ) {
            return Blob_to_Bytea;
        }
        if( Blob.class.isAssignableFrom(toClass) && byte[].class == fromClass ) {
            return new ReverseConverter<>(Blob_to_Bytea);
        }

        if( Reader.class.isAssignableFrom(fromClass) && String.class == toClass )
            return Reader_to_String;
        if( Reader.class.isAssignableFrom(toClass) && String.class == fromClass )
            return new ReverseConverter<>( Reader_to_String );

        if( Reader.class.isAssignableFrom(fromClass) && char[].class == toClass   )
            return Reader_to_Chars;
        if( Reader.class.isAssignableFrom(toClass  ) && char[].class == fromClass )
            return new ReverseConverter<>( Reader_to_Chars );

        if( InputStream.class.isAssignableFrom(fromClass) && byte[].class == toClass   )
            return InputStream_to_Bytes;
        if( InputStream.class.isAssignableFrom(toClass  ) && byte[].class == fromClass )
            return new ReverseConverter<>( InputStream_to_Bytes );

        return null;
    }

    /**
     *
     */
    public static <T> IConverter<Object,T> getConverter( Class fromClass, Class toClass ) {

        IConverter<Object,T> c = g_converterMap.get( new PairClass(fromClass, toClass) );
        
        if( c != null )
            return c;

        c = g_converterMap.get( new PairClass(toClass, fromClass) );
        
        if( c != null ) {

            registerConverter( fromClass, toClass, new ReverseConverter<>(c) );

            c = g_converterMap.get( new PairClass(fromClass, toClass) );    
        }

        if( c == null ) {

            c = getInterfaceConverter(fromClass, toClass);

            registerConverter( fromClass, toClass, c );
        }

        return c;
    }

    /** */
    public static <T> IConverter registerConverter( Class fromClass, Class toClass, IConverter converter ) {
        return g_converterMap.put( new PairClass( fromClass, toClass ), converter );
    }

    /** */
    public static <T> IConverter registerStructConverter( String fromStruct, Class<T> toClass, IConverter<T,Struct> converter ) {
        return g_structConverterMap.put( new PairStringClass( fromStruct, toClass ), converter );
    }

    /** */
    public static <T> IConverter getStructConverter( String fromStruct, Class<T> toClass) {
        return g_structConverterMap.get( new PairStringClass( fromStruct, toClass ) );
    }

    /**
     *
     */
    public static boolean hasConverter(Class fromClass, Class toClass) {
        return g_converterMap.containsKey(new PairClass(fromClass, toClass));
    }

    /**
     *  Получить класс форматирования
     */
    public static <K> IConverter<K, String> getFormatConverter( Class fromClass, String mask ) {
        
        if( fromClass == java.sql.Date.class ) 
        {
            if( S.isNullOrEmpty(mask) )
                return (IConverter< K, String >)SqlDate_to_String;
            
            return (IConverter<K, String>) new IConverter<java.sql.Date, String>() {
                private final SimpleDateFormat format = new SimpleDateFormat( mask );
                @Override
                public String to(java.sql.Date value) {
                    return value == null ? null : format.format(value);
                }
                @Override
                public java.sql.Date from( String value ) {
                    try {
                        return value == null ? null : convert( format.parse(value), java.sql.Date.class );
                    } catch (ParseException ex) {
                        TypeConverterException.fromValueToClass(value, java.sql.Date.class, ex, "getFormatConverter: java.sql.Date, String");
                    }
                    return null; //stub
                }
            };
        }
        
        if( fromClass == LocalDate.class )
        {
            if( S.isNullOrEmpty(mask) )
                return LocalDate_to_String;
            
            return (IConverter<K, String>) new IConverter<LocalDate, String>() {
                private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern( mask.replaceAll("[H:m:s]", "") );
                @Override
                public String to(LocalDate value) {
                    return value == null ? null : formatter.format(value);
                }
                @Override
                public LocalDate from( String value ) {
                    return value == null ? null : LocalDate.parse( value, formatter );
                }
            };
        }

        if( fromClass == LocalTime.class )
        {
            if( S.isNullOrEmpty(mask) )
                return LocalTime_to_String;
            
            return (IConverter<K, String>) new IConverter<LocalTime, String>() {
                private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern( mask );
                @Override
                public String to(LocalTime value) {
                    return  value == null ? null : formatter.format(value);
                }
                @Override
                public LocalTime from( String value ) {
                    return  value == null ? null : LocalTime.parse( value, formatter );
                }
            };
            
        }
        
        
        if( fromClass == LocalDateTime.class )
        {
            if( S.isNullOrEmpty(mask) )
                return LocalDateTime_to_String;
            
            return (IConverter<K, String>) new IConverter<LocalDateTime, String>() {
                private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern( mask );
                @Override
                public String to(LocalDateTime value) {
                    return  value == null ? null : formatter.format(value);
                }
                @Override
                public LocalDateTime from( String value ) {
                    return  value == null ? null : LocalDateTime.parse( value, formatter );
                }
            };
            
        }
        
        if( fromClass == java.util.Date.class )
        {
            if( S.isNullOrEmpty(mask) )
                return (IConverter< K, String >)UtilDate_to_String;
            
            return (IConverter<K, String>) new IConverter<java.util.Date, String>() {
                private final SimpleDateFormat format = new SimpleDateFormat( mask );
                @Override
                public String to(java.util.Date value) {
                    return  value == null ? null : format.format(value);
                }
                @Override
                public java.util.Date from( String value ) {
                    try {
                        return value == null ? null : format.parse(value);
                    } catch (ParseException ex) {
                        TypeConverterException.fromValueToClass(value, java.util.Date.class, ex, "getFormatConverter: java.util.Date, String");
                    }
                    return null; //stub
                }
            };
        }

        if( fromClass == Timestamp.class )
        {
            if( S.isNullOrEmpty(mask) )
                return Timestamp_to_String;

            return (IConverter<K, String>) new IConverter<Timestamp, String>() {
                private final SimpleDateFormat format = new SimpleDateFormat( mask );
                @Override
                public String to(Timestamp value) {
                    return  value == null ? null : format.format(value);
                }
                @Override
                public Timestamp from( String value ) {
                    try {
                        return value == null ? null : new Timestamp( format.parse(value).getTime() );
                    } catch (ParseException ex) {
                        TypeConverterException.fromValueToClass(value, java.util.Date.class, ex, "getFormatConverter: Timestamp, String");
                    }
                    return null; //stub
                }
            };
        }

        throw new IllegalArgumentException("No formatConverter for class " + fromClass );
    }

    /** */
    public static void main(String[] args) {
    
        try {

            String ds = "2023-12-21 10:11:03";

            try {

                LocalDateTime ld = TypeConverter.convert( ds, LocalDateTime.class );
                System.out.println("date: " + ld );

            }
            catch( Throwable th ) {
                th.printStackTrace();
            }

//            String as[] = {"123","1234.9"," 123 34.90", "+37908740 , 09", "-0",
//                    "120981209845898", "1 2 3 9090909090909090009"
//            };
//
//            for( String s : as ) {
//
//                try {
//
//                    Number n =  TypeConverter.convert( s, Number.class );
//
//                    System.out.println (
//                        n + " (" + n.getClass() + ")"
//                    );
//                }catch( Throwable th ) {
//                    th.printStackTrace();
//                }
//            }

        }
        catch( Throwable th ) {
            th.printStackTrace();
        }
        
    }
    
}
