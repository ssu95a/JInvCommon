package ru.inversion.utils.ini;

import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** */
public class IniFileEventReader_old implements Iterable<IniFileEvent>, AutoCloseable {

    private static final Charset WINDOWS_1251 = Charset.forName("Windows-1251");

    final private BufferedReader br;
    final private String name;
    final private Iterator<String> iter;

    /** Признак, что символ - ; в значениях параметров является частью значения, а не началом комментария */
    final private boolean semicolonPartOfValue;

    /**
     * If true, '#' inside parameter value is not an INI comment.
     * Default false keeps legacy behavior.
     */
    private final boolean hashPartOfValue;

    /** */
    private IniFileEventReader_old( Reader r, String name, boolean semicolonPartOfValue, boolean hashPartOfValue  ) {

        this.name = name;

        if( r instanceof BufferedReader )
            br = (BufferedReader)r;
        else
            br = new BufferedReader(r);

        iter = br.lines().map(String::trim).filter(S::isNotNullOrEmpty).iterator();

        this.semicolonPartOfValue = semicolonPartOfValue;
        this.hashPartOfValue = hashPartOfValue;
    }

    /** */
    @Override
    public Iterator<IniFileEvent> iterator( ) {

        return new Iterator<IniFileEvent>() {

            private boolean eof = false;
            private int lineCounter = 0;

            private IniFileEvent tempEvent = null;

            {
                tempEvent = new IniFileEvent( IniFileEventReader_old.this, IniFileEvent.Type.StartFile, name, -1, null );
            }

            @Override
            public boolean hasNext() {
                return !eof;
            }

            @Override
            public IniFileEvent next() {

                if( tempEvent != null )
                {
                    IniFileEvent e = tempEvent;
                    tempEvent = null;
                    return e;
                }

                if( eof )
                    throw new NoSuchElementException("'eof' is true");

                if( !iter.hasNext() ) {
                     eof = true;
                     return new IniFileEvent( IniFileEventReader_old.this, IniFileEvent.Type.Eof, name, -1, null );
                }

                lineCounter++;

                final String line;

                line = iter.next();

                if( line.charAt(0) == ';' || line.charAt(0) == '#' )
                {
                    String comment = line.substring(1);

//                    if( S.isNullOrEmpty(comment) )
//                        return next();

                    return new IniFileEvent( IniFileEventReader_old.this, IniFileEvent.Type.Comment, comment, -1, null );
                }

                // Обработка секций [section]
                if( line.charAt(0) == '[' )
                {
                    if( S.lastChar(line) != ']' )
                        throw new IllegalStateException( "Не закрытая секция в файле: " + line + ", в строке " + lineCounter );

                    return new IniFileEvent( IniFileEventReader_old.this, IniFileEvent.Type.Section, line.substring( 1, line.length() - 1 ).trim(), -1, null );
                }

                return processPair(line);
            }

            /** */
            private int parseDoubleQuotes( char[] chars, int i, StringBuilder sb )
            {
                while(++i < chars.length && chars[i] != '"') {
                      sb.append(chars[i]);
                }
                return i;
            }

            /** */
            private int parseSingleQuotes( char[] chars, int i, StringBuilder sb )
            {
                while(++i < chars.length && chars[i] != '\'') {
                      sb.append(chars[i]);
                }
                return i;
            }

            /** */
            private int parseEscapeChar( char[] chars, int i, StringBuilder sb ) {

                // chars[i] == '\'

                if( ++i >= chars.length ) {
                    /*
                     * Одинокий backslash в конце значения.
                     * Безопаснее сохранить его как символ, а не молча потерять.
                     */
                    sb.append('\\');
                    return i;
                }

                char escapeChar = chars[i];

                switch (escapeChar) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case '\'':
                        sb.append('\'');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    default:
                        /*
                         * Неизвестные escape-последовательности сохраняем как есть.
                         */
                    sb.append('\\');
                    sb.append(escapeChar);
                    break;
                }

                return i;
            }

            /** */
            private int parseComment( char[] chars, int i, StringBuilder sb )
            {
                if( chars[i] == ';' && semicolonPartOfValue ) {
                    sb.append(';');
                    return i;
                }

                if( chars.length > i + 1 )
                    tempEvent = new IniFileEvent(IniFileEventReader_old.this, IniFileEvent.Type.Comment, new String( chars, i + 1, chars.length - i - 1 ), -1, null );

                return chars.length;
            }

            /** */
            private String processValue( String value )
            {
                if( S.isNullOrEmpty(value) )
                    return value;

                value = value.trim();

                if( semicolonPartOfValue )
                    if( !S.contains( value, '\\', '#', '"', '\'') )
                        return value;
                else
                    if( !S.contains( value, '\\', ';', '#', '"', '\'') )
                         return value;

                final char[] chars = value.toCharArray();

                final StringBuilder sb = new StringBuilder();

                for( int i = 0; i < chars.length; i++ )
                {
                    char ch = chars[i];

                    switch( ch ) {
                        case '"':
                            i = parseDoubleQuotes( chars, i, sb );
                        break;
                        case '\'':
                            i = parseSingleQuotes( chars, i, sb );
                        break;
                        case '\\':
                            i = parseEscapeChar( chars, i, sb );
                        break;
                        case '#': //comment
                        case ';': //comment
                            i = parseComment( chars, i, sb );
                        break;
                        default:
                            sb.append(ch);
                    }
                }
                return sb.toString();
            }

            /** */
            private IniFileEvent processPair( String line )
            {
                final int index = line.indexOf('=');

                if( index > 0 )
                {
                    final String key   = line.substring(0, index ).trim();
                    final String value = line.substring(index + 1);
                    return new IniFileEvent( IniFileEventReader_old.this, key, processValue(value), -1, null );
                }
                throw new IllegalStateException("Параметр без знака '=' в строке " + lineCounter + ", строка: " + line );
            }
        };
    }

    @Override
    public void close() throws Exception {
        br.close();
    }

    /** */
    public Stream<IniFileEvent> stream( ) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize( this.iterator(), Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    /** */
    public static class Builder {

        private Object iniFile = null;
        private boolean semicolonPartOfValue = true;
        private Charset charset = WINDOWS_1251;

        /** */
        public Builder iniFile( Object iniFile )
        {
            this.iniFile = iniFile;
            return this;
        }

        /** */
        public Builder charset( Charset v )
        {
            this.charset = v;
            return this;
        }

        /** */
        public Builder semicolonPartOfValue( boolean v )
        {
            this.semicolonPartOfValue = v;
            return this;
        }

        /** */
        public IniFileEventReader_old build( )
        {
            if( iniFile == null )
                throw new IllegalArgumentException( "Необходимо задать источник данных 'iniFile'", new NullPointerException("'iniFile' is null") );

            final Path file;

            if( iniFile instanceof String )
                file = Paths.get((String)iniFile);
            else if( iniFile instanceof File )
                file = ((File)iniFile).toPath();
            else if( iniFile instanceof Path )
                file = ((Path)iniFile);
            else
                throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "Тип данных '" + iniFile.getClass().getName() + "' не может использоваться в качестве источника данных");

            final BufferedReader br;
            try {
                br = Files.newBufferedReader( file, U.nvl( charset, WINDOWS_1251 ) );
            }
            catch( IOException e ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Ошибка при открытии файла", e );
            }

            return new IniFileEventReader_old( br, file.getFileName().toString(), semicolonPartOfValue, true );
        }
    }
    /** */
    public static Builder newBuilder() {
        return new Builder();
    }

    /** */
    public static void main( String[] args )
    {
        try {

            //
            //
            final IniFileEventReader_old r = IniFileEventReader_old.newBuilder().iniFile("d:\\ALTPRNT5.INI").semicolonPartOfValue(true).build();
            //("d:\\ALTPRNT5.INI", true)
           for( IniFileEvent e : r ) {
               System.out.println(e);
           }

        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

}
