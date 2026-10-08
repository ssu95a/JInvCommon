package ru.inversion.utils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author ssu @
 */
public class S {

    final public static String EMPTY_STRING = "";

    final public static String SPACE = " ";

    /**
     * <p>The maximum size to which the padding constant(s) can expand.</p>
     */
    private static final int PAD_LIMIT = 8192;

    /** */
    public static boolean isString( Object v ) {
        return v instanceof String;
    }

    /** */
    public static String prepend( String pre, String base ) {
        return ( pre == null || pre.isEmpty()) ? base : ( pre + base );
    }

    /**
     * Заключает переданную строку в кавычки
     */
    public static String quote( String s ) {
        return isNullOrEmpty(s) ? EMPTY_STRING : '"' + s + '"';
    }

    /**
     * Заключает переданную строку в апострофы
     */
    public static String apstrph( String s ) {
        return isNullOrEmpty(s) ? "''" : new StringBuilder( s.length() + 2 ).append("'").append( s ).append("'").toString();
    }

    /** */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** */
    public static boolean isNotNullOrEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    /**
     * Конвертирует упрощённое SQL LIKE-выражение в Java RegEx-выражение.
     *
     * Поддерживает:
     *   % -> .*
     *   _ -> .
     *
     * SQL ESCAPE не поддерживается.
     */
    public static String likeToRegexp(String likeExpression) {

        if( isNullOrEmpty(likeExpression) )
            return likeExpression;

        StringBuilder sb = new StringBuilder(likeExpression.length() + 8);

        sb.append('^');

        for (int i = 0; i < likeExpression.length(); i++) {
            char ch = likeExpression.charAt(i);

            switch (ch) {
                case '%':
                    sb.append(".*");
                    break;

                case '_':
                    sb.append('.');
                    break;

                case '\\':
                case '.':
                case '^':
                case '$':
                case '|':
                case '?':
                case '*':
                case '+':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                    sb.append('\\').append(ch);
                    break;

                default:
                    sb.append(ch);
                    break;
            }
        }

        sb.append('$');

        return sb.toString();
    }

    /**
     * Возвращает последний символ в строке
     */
    public static char lastChar(String s) {
        return isNullOrEmpty(s) ? (char) 0 : s.charAt(s.length() - 1);
    }

    /** */
    private static boolean in( String s, Function<String,Boolean> f, String ... sl ) {

        if( sl == null || sl.length ==0 )
            return false;

        if( s == null )
            return U.containsNull( sl );

        for( String v : sl ) {
             if( f.apply(v) )
                 return true;
        }
        return false;
    }

    /** */
    public static boolean in( String s, String ... sl ) {
        return in( s, s::equals, sl );
    }

    /** */
    public static boolean inIgnoreCase( String s, String ... sl ) {
        return in( s, s::equalsIgnoreCase, sl );
    }

    /**
     * Регистронезависимый поиск индекса подстроки
     */
    public static int indexOfIgnoreCase(String source, String target) {

        Collator collator = Collator.getInstance();

        collator.setStrength(Collator.PRIMARY);

        for( int i = 0; i <= source.length() - target.length(); i++) {
             if( collator.equals(target, source.substring(i, i + target.length()))) {
                return i;
             }
        }
        return -1;
    }

    /**
     * Регистронезависимый поиск индекса подстроки
     */
    public static int indexOfIgnoreCase(StringBuilder sb, String target) {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        for (int i = 0; i <= sb.length() - target.length(); i++) {
            if (collator.equals(target, sb.substring(i, i + target.length()))) {
                return i;
            }
        }
        return -1;
    }

    /** */
    public static String insert( String s, int index, CharSequence cs ) {
        StringBuilder sb = new StringBuilder(s.length() + cs.length());
        sb.append( s );
        sb.insert( index, cs );
        return sb.toString();
    }

    /** */
    public static String nvl(Object o) {
        return o == null ? "<null>" : o.toString();
    }

    /** */
    public static String nz(Object o) {
        return o == null ? EMPTY_STRING : o.toString();
    }

    /** */
    public static String nullIfEmpty( String s ) {
        return ( s == null || s.isEmpty() ) ? null : s;
    }

    /** */
    public static String space( int length, char ch ) {

        StringBuilder sb = new StringBuilder( length );

        for( int i = 0; i < length; i++ ) {
             sb.append(ch);
        }

        return sb.toString();
    }

    /** */
    public static int countOf( String s, char ch ) {

        if( isNullOrEmpty(s) )
            return 0;

        return (int)s.chars().filter( (i)-> i==(int)ch ).count();
    }

    /** */
    public static <T> String join( Collection<T> c, CharSequence delimiter ) {
        return c.stream()
                .filter ( Objects::nonNull )
                .map    ( Objects::toString )
                .collect( Collectors.joining( delimiter ) );
    }



    /*
        public static <T> T decodeIgnoreCase( String s, Objects ... o ) {}
    */

    public static int compareToIgnoreCase( String s1, String s2 ) {
        if( s1 == null && s2 == null )
            return 0;
        if( s1 == null )
            return -1;
        if( s2 == null )
            return  1;
        return s1.compareToIgnoreCase( s2 );
    }

    /** */
    @SuppressWarnings("unchecked")
    public static <R> R decodeIgnoreCase( String value, Object ... vs ) {

        if( vs == null || vs.length < 2 )
            throw new IllegalArgumentException("vs size must be > 1");

        for( int i = 0; i < vs.length; i = i + 2 ) {
            String v = (String)vs[i];
            if( compareToIgnoreCase(value, v) == 0 )
                if( i < vs.length-1 )
                    return (R)vs[i+1];
        }
        if( vs.length % 2 == 0 )
            return null;
        return (R)vs[vs.length-1];
    }


    public static String translate( String s, CharSequence to_replace, CharSequence replacement ) {

        if( U.containsNull( s, to_replace, replacement ) )
            return s;

        final int length = Math.min( to_replace.length(), replacement.length() );

        if( length == 0 )
            return s;

        Function<Character,Integer> find_char = new Function< Character, Integer >() {
            @Override
            public Integer apply( Character ch ) {
                for( int j = 0; j < length; j++  )
                     if( to_replace.charAt(j) == ch )
                         return j;
                return -1;
            }
        };

        StringBuilder sb = new StringBuilder();
        char ch;
        int index;

        for( int i = 0; i < s.length(); i++ ) {

            ch = s.charAt(i);

            index = find_char.apply(ch);

            if( index >= 0 )
                ch = replacement.charAt(index);

            sb.append(ch);
        }

        return sb.toString();
    }

    static final private int BUFFER_SIZE = 1024*2;

    /** */
    public static String toString( Reader r ) {

        if( r == null )
            return null;

        StringBuilder sb = new StringBuilder();

        try {

            char[] buffer = new char[BUFFER_SIZE];

            int n;

            while( ( n = r.read(buffer) ) != -1)
                  sb.append(buffer,0,n);

            return sb.toString();

        }catch( IOException iex) {
            throw new RuntimeException( "Error on read Reader to String", iex );
        }
    }

    /** */
    static public boolean contains( String s, char ... chs ) {

        if(s == null )
            return false;

        for( char ch : chs )
            if( s.indexOf(ch) != -1 )
                return true;

        return false;
    }

    /** */
    public static String firstNonBlank( String... values) {

        if( values == null )
            return null;

        for( String v : values ) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /**
     * Генерация уникально значение по вх. строке заданной длинны
     *
     * @param input
     * @param length
     * @return
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.io.UnsupportedEncodingException
     *
     * просьба эту х...ню не использовать!
     */
    public static String getMd5Hash(String input, int length) throws NoSuchAlgorithmException, UnsupportedEncodingException {

        MessageDigest m = MessageDigest.getInstance("MD5");
        byte[] data = m.digest(input.getBytes("UTF8"));
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < data.length; i++) {

            for (byte b : data) {
                if (b == 0x00) {
                    sBuilder.append("00");
                } else if ((b & 0x0F) == b) {
                    sBuilder.append("0");
                    break;
                } else {
                    break;
                }
            }

            BigInteger bigInt = new BigInteger(1, data);
            sBuilder.append(bigInt.toString(16));
        }

        // Return the hexadecimal string.
        return sBuilder.toString().substring(0, length);
    }

    /**
     * Формирует строку, если поле ограничено по длинне
     *
     * @param str имя из контрола
     * @return
     *
     * ... и эту тоже!
     */
    public static String trimLongString(String str, int length) throws NoSuchAlgorithmException, UnsupportedEncodingException {

        int postfixLength = 9;
        if (str == null) {
            return null;
        }

        if (str.length() <= length) {
            return str.toUpperCase();
        }

        int shortNameLength = length - 1 - postfixLength;

        String postfix = S.getMd5Hash(str, postfixLength);
        //берем последние prefix_length символов и добавляем postfix
        return (str.substring(str.length() - shortNameLength) + "@" + postfix).toUpperCase();
    }

    /**
     * <p>Right pad a String with spaces (' ').</p>
     *
     * <p>The String is padded to the size of {@code size}.</p>
     *
     * <pre>
     * S.rPad(null, *)   = null
     * S.rPad("", 3)     = "   "
     * S.rPad("bat", 3)  = "bat"
     * S.rPad("bat", 5)  = "bat  "
     * S.rPad("bat", 1)  = "bat"
     * S.rPad("bat", -1) = "bat"
     * </pre>
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @return right padded String or original String if no padding is necessary,
     *  {@code null} if null String input
     */
    public static String rPad(final String str, final int size) {
        return rPad(str, size, ' ');
    }

    /**
     * <p>Right pad a String with a specified character.</p>
     *
     * <p>The String is padded to the size of {@code size}.</p>
     *
     * <pre>
     * S.rPad(null, *, *)     = null
     * S.rPad("", 3, 'z')     = "zzz"
     * S.rPad("bat", 3, 'z')  = "bat"
     * S.rPad("bat", 5, 'z')  = "batzz"
     * S.rPad("bat", 1, 'z')  = "bat"
     * S.rPad("bat", -1, 'z') = "bat"
     * </pre>
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return right padded String or original String if no padding is necessary,
     *  {@code null} if null String input
     * @since 2.0
     */
    public static String rPad(final String str, final int size, final char padChar) {
        if (str == null) {
            return null;
        }
        final int pads = size - str.length();
        if (pads <= 0) {
            return str; // returns original String when possible
        }
        if (pads > PAD_LIMIT) {
            return rPad(str, size, String.valueOf(padChar));
        }
        return str.concat(repeat(padChar, pads));
    }

    /**
     * <p>Right pad a String with a specified String.</p>
     *
     * <p>The String is padded to the size of {@code size}.</p>
     *
     * <pre>
     * S.rPad(null, *, *)      = null
     * S.rPad("", 3, "z")      = "zzz"
     * S.rPad("bat", 3, "yz")  = "bat"
     * S.rPad("bat", 5, "yz")  = "batyz"
     * S.rPad("bat", 8, "yz")  = "batyzyzy"
     * S.rPad("bat", 1, "yz")  = "bat"
     * S.rPad("bat", -1, "yz") = "bat"
     * S.rPad("bat", 5, null)  = "bat  "
     * S.rPad("bat", 5, "")    = "bat  "
     * </pre>
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padStr  the String to pad with, null or empty treated as single space
     * @return right padded String or original String if no padding is necessary,
     *  {@code null} if null String input
     */
    public static String rPad(final String str, final int size, String padStr) {
        if (str == null) {
            return null;
        }
        if (padStr.isEmpty()) {
            padStr = SPACE;
        }
        final int padLen = padStr.length();
        final int strLen = str.length();
        final int pads = size - strLen;
        if (pads <= 0) {
            return str; // returns original String when possible
        }
        if (padLen == 1 && pads <= PAD_LIMIT) {
            return rPad(str, size, padStr.charAt(0));
        }

        if (pads == padLen) {
            return str.concat(padStr);
        } else if (pads < padLen) {
            return str.concat(padStr.substring(0, pads));
        } else {
            final char[] padding = new char[pads];
            final char[] padChars = padStr.toCharArray();
            for (int i = 0; i < pads; i++) {
                padding[i] = padChars[i % padLen];
            }
            return str.concat(new String(padding));
        }
    }

    /**
     * <p>Left pad a String with spaces (' ').</p>
     *
     * <p>The String is padded to the size of {@code size}.</p>
     *
     * <pre>
     * S.lPad(null, *)   = null
     * S.lPad("", 3)     = "   "
     * S.lPad("bat", 3)  = "bat"
     * S.lPad("bat", 5)  = "  bat"
     * S.lPad("bat", 1)  = "bat"
     * S.lPad("bat", -1) = "bat"
     * </pre>
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @return left padded String or original String if no padding is necessary,
     *  {@code null} if null String input
     */
    public static String lPad(final String str, final int size) {
        return lPad(str, size, ' ');
    }

    /**
     * <p>Left pad a String with a specified character.</p>
     *
     * <p>Pad to a size of {@code size}.</p>
     *
     * <pre>
     * S.lPad(null, *, *)     = null
     * S.lPad("", 3, 'z')     = "zzz"
     * S.lPad("bat", 3, 'z')  = "bat"
     * S.lPad("bat", 5, 'z')  = "zzbat"
     * S.lPad("bat", 1, 'z')  = "bat"
     * S.lPad("bat", -1, 'z') = "bat"
     * </pre>
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *  {@code null} if null String input
     * @since 2.0
     */
    public static String lPad(final String str, final int size, final char padChar) {
        if (str == null) {
            return null;
        }
        final int pads = size - str.length();
        if (pads <= 0) {
            return str; // returns original String when possible
        }
        if (pads > PAD_LIMIT) {
            return lPad(str, size, String.valueOf(padChar));
        }
        return repeat(padChar, pads).concat(str);
    }

    /**
     * <p>Left pad a String with a specified String.</p>
     *
     * <p>Pad to a size of {@code size}.</p>
     *
     * <pre>
     * S.lPad(null, *, *)      = null
     * S.lPad("", 3, "z")      = "zzz"
     * S.lPad("bat", 3, "yz")  = "bat"
     * S.lPad("bat", 5, "yz")  = "yzbat"
     * S.lPad("bat", 8, "yz")  = "yzyzybat"
     * S.lPad("bat", 1, "yz")  = "bat"
     * S.lPad("bat", -1, "yz") = "bat"
     * S.lPad("bat", 5, null)  = "  bat"
     * S.lPad("bat", 5, "")    = "  bat"
     * </pre>
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padStr  the String to pad with, null or empty treated as single space
     * @return left padded String or original String if no padding is necessary,
     *  {@code null} if null String input
     */
    public static String lPad(final String str, final int size, String padStr) {
        if (str == null) {
            return null;
        }
        if (padStr.isEmpty()) {
            padStr = SPACE;
        }
        final int padLen = padStr.length();
        final int strLen = str.length();
        final int pads = size - strLen;
        if (pads <= 0) {
            return str; // returns original String when possible
        }
        if (padLen == 1 && pads <= PAD_LIMIT) {
            return lPad(str, size, padStr.charAt(0));
        }

        if (pads == padLen) {
            return padStr.concat(str);
        } else if (pads < padLen) {
            return padStr.substring(0, pads).concat(str);
        } else {
            final char[] padding = new char[pads];
            final char[] padChars = padStr.toCharArray();
            for (int i = 0; i < pads; i++) {
                padding[i] = padChars[i % padLen];
            }
            return new String(padding).concat(str);
        }
    }

    static String repeat(final char ch, final int repeat) {
        if (repeat <= 0) {
            return EMPTY_STRING;
        }
        final char[] buf = new char[repeat];
        for (int i = repeat - 1; i >= 0; i--) {
            buf[i] = ch;
        }
        return new String(buf);
    }

    static String removeEnd(final String str, final String remove) {
        if (str.isEmpty() || remove.isEmpty()) {
            return str;
        }
        if (str.endsWith(remove)) {
            return str.substring(0, str.length() - remove.length());
        }
        return str;
    }
//    public static void main(String[] args) throws NoSuchAlgorithmException, UnsupportedEncodingException {
//        String st = "12345678901 234567890";
//        System.out.println( S.countOf(st,'x'));
//    }
}
