package ru.inversion.utils.ini;

import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
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

/**
 * <h6>Низкоуровневый однопроходный, одноразовый INI-reader.</h6>
 * Предназначен для чтения Win INI файлов с параметрами!
 * <p>
 * Контракт:
 * <ol>
 * <li>Reader является Iterator<IniFileEvent>, это потоковый одноразовый читатель. Повторный проход по тому же reader-у невозможен.</li>
 * <li> Reader возвращает события:</li>
 * <ul>
 *      <li>StartFile</li>
 *      <li>Section</li>
 *      <li>Parameter</li>
 *      <li>Comment</li>
 *      <li>Eof</li>
 * </ul>
 *</ul>
 * <li>Пустые строки пропускаются.<br>
 *     Номер строки lineNo при этом считается по физическим строкам файла.</li>
 *
 * <li>Каждая непустая физическая строка даёт максимум одно событие.</li>
 *
 * <li>Полнострочные комментарии возвращаются как Comment event.
 *    <br>Строки:
 *    <ul>
 *      <li>; comment</li>
 *      <li># comment</li>
 *   </ul>
 *   дают:<br>
 *      Comment(" comment")
 *</li>
 * <li>Inline-комментарии внутри строки параметра НЕ возвращаются отдельным событием.<br>
 *    Они только завершают значение параметра.<br>
 *    Пример:при <pre>semicolonPartOfValue=false</pre><br>
 *      A = 123 ; comment text
 *     даёт:
 *      Parameter(key="A", value="123")
 * <br>
 *    Комментарий "comment text" теряется намеренно.
 *    Если комментарий важен, он должен быть записан отдельной строкой.</li>
 *
 * <li>По умолчанию символы ';' и '#' внутри значения считаются началом inline-комментария.
 *
 *    Пример:
 *      RootKey = HKEY_LOCAL_MACHINE #comment
 *
 *    даёт:
 *      Parameter(key="RootKey", value="HKEY_LOCAL_MACHINE")</li>
 *
 * <li> Если ';' должен быть частью значения, нужно включить:
 *
 *      semicolonPartOfValue(true)
 *
 *      например:
 *
 *      NORMAL=Name Font=Courier New;Size Font=10;Bold=No;
 *<br>
 *      При semicolonPartOfValue=true значение не режется по ';'.</li>
 *
 *<li>Если '#' должен быть частью значения, нужно включить: hashPartOfValue(true)</li>
 *
 *<li>Символы ';' и '#' внутри кавычек не считаются комментариями.
 *
 *     Пример:
 *       A = "123 # not comment"
 *
 *     даёт:
 *       Parameter(key="A", value="123 # not comment")
 *
 *<li>Внешние пробелы вокруг key и value незначащие.
 *<br>
 *     Пример:
 *       A   =   123,
 *
 *     даёт:
 *       key="A"
 *       value="123"
 *<br>
 *     Внутренние пробелы значения сохраняются.
 *</li>
 *<li>Значение в кавычках возвращается без самих кавычек.
 *<br>
 *     Пример:
 *       A = "hello",
 *
 *     даёт:
 *       value=hello
 *</li>
 *<li>Известные escape-последовательности декодируются:
 * <pre>
 *       \n  -> LF
 *       \r  -> CR
 *       \t  -> TAB
 *       \b  -> BACKSPACE
 *       \f  -> FORM FEED
 *       \"  -> "
 *       \'  -> '
 *       \\  -> \
 * </pre></li>
 *<li>Неизвестные escape-последовательности сохраняются как есть.</li>
 *
 *<li>Строка секции должна иметь вид:
 *<br>
 *     [SectionName]
 *<br>
 *     Если закрывающей ']' нет, reader бросает IllegalStateException
 *     с именем файла, номером строки и rawLine.
 *</li>
 *<li>Строка параметра должна иметь вид:
 *<br>
 *     key = value
 *<br>
 *     Если '=' отсутствует или key пустой, reader бросает IllegalStateException
 *     с именем файла, номером строки и rawLine.
 *</li>
 *<li>Каждое событие содержит:
 *<pre>
 *       type
 *       key
 *       value
 *       lineNo
 *       rawLine
 *</pre>
 *<p>
 *     Для StartFile/Eof:
 *       lineNo = -1
 *       rawLine = null
 *</li>
 */
public final class IniFileEventReader implements Iterator<IniFileEvent>, AutoCloseable {

    private static final Charset WINDOWS_1251 = Charset.forName("Windows-1251");

    private final BufferedReader br;
    private final String name;

    /**
     * Если true, ';' внутри значения параметра воспринимаются как часть значения
     */
    private final boolean semicolonPartOfValue;

    /**
     * Если значение true, то '#' внутри параметра value является частью значения,
     * а не маркером встроенного комментария.
     * Значение false по умолчанию сохраняет классическое поведение, подобное INI.
     */
    private final boolean hashPartOfValue;

    private IniFileEvent nextEvent;

    private boolean closed = false;

    private int    lineNo = 0;
    private String lineRaw;

    /** */
    private IniFileEventReader( Reader reader, String name, boolean semicolonPartOfValue, boolean hashPartOfValue )
    {
        if( reader == null )
            throw new IllegalArgumentException( "reader is null" );

        if( reader instanceof BufferedReader )
            this.br = (BufferedReader) reader;
        else
            this.br = new BufferedReader(reader);

        this.name = name == null ? S.EMPTY_STRING : name;

        this.semicolonPartOfValue = semicolonPartOfValue;
        this.hashPartOfValue      = hashPartOfValue;

        this.nextEvent = new IniFileEvent( this, IniFileEvent.Type.StartFile, name, -1, null );
    }

    /** */
    @Override
    public boolean hasNext( )
    {
        return nextEvent != null;
    }

    /** */
    @Override
    public IniFileEvent next( ) {

        if( !hasNext() )
            throw new NoSuchElementException( );

        IniFileEvent returnEvent = nextEvent;

        nextEvent = readNextEvent( );

        return returnEvent;
    }


    /** */
    private IniFileEvent readNextEvent()
    {
        if( closed )
            return null;

        String line = readNonEmptyTrimmedLine( );

        if( line == null )
        {
            closed = true;
            return new IniFileEvent( this, IniFileEvent.Type.Eof, name, -1, null);
        }

        char first = line.charAt(0);

        if( first == ';' || first == '#' )
            return new IniFileEvent( this, IniFileEvent.Type.Comment, line.substring(1), lineNo, lineRaw );

        if( first == '[' )
            return processSection(line);

        return processPair(line);
    }

    /**
     * Считывает следующую непустую физическую строку.
     */
    private String readNonEmptyTrimmedLine()
    {

        try {

            String raw;

            while( (raw = br.readLine()) != null )
            {
                lineNo++;
                lineRaw = raw;

                String line = raw.trim();

                if( line.isEmpty() )
                    continue;

                return line;
            }

            lineRaw = null;

            return null;
        }
        catch( IOException e ) {
            throw new UncheckedIOException( "Ошибка чтения INI файла: " + name, e );
        }
    }

    /** */
    private IniFileEvent processSection( String line )
    {
        if( S.lastChar(line) != ']')
            throw onIllegalState("Не закрытая секция");

        String section = line.substring(1, line.length() - 1).trim();

        return new IniFileEvent( this, IniFileEvent.Type.Section, section, lineNo, lineRaw );
    }

    /** */
    private IniFileEvent processPair(String line)
    {
        int index = line.indexOf('=');

        if( index <= 0 )
            throw onIllegalState("Параметр без знака '='");

        String key = line.substring( 0, index).trim();

        if( key.isEmpty() )
            throw onIllegalState( "Пустое имя параметра" );

        String rawValue = line.substring(index + 1);
        String value    = processValue( rawValue );

        return new IniFileEvent( this, key, value, lineNo, lineRaw );
    }

    private String processValue(String value)
    {
        if( S.isNullOrEmpty(value) )
            return value;

        // Пробелы снаружи режем
        value = value.trim();

        if( value.isEmpty() )
            return value;

        // Если значение не содержит специальных символов, возвращаем его как есть.
        if( !needParsingValue(value) )
            return value;

        final char[] chars = value.toCharArray();
        final StringBuilder sb = new StringBuilder(value.length());

        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean stoppedByComment = false;

        for( int i = 0; i < chars.length; i++ )
        {
             char ch = chars[i];

            if( ch == '\\' ) {
                i = parseEscapeChar(chars, i, sb);
                continue;
            }

            if( ch == '"' && !inSingleQuotes ) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if( ch == '\'' && !inDoubleQuotes ) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if( !inDoubleQuotes && !inSingleQuotes && isCommentStart(ch)) {
                stoppedByComment = true;
                break;
            }

            sb.append(ch);
        }

        String result = sb.toString();

        return stoppedByComment ? rtrim(result) : result;
    }

    /** */
    private boolean needParsingValue( String value )
    {
        if( S.isNullOrEmpty(value))
            return false;

        if( value.indexOf('\\') >= 0)
            return true;

        if( value.indexOf('"') >= 0 )
            return true;

        if( value.indexOf('\'') >= 0)
            return true;

        if (!semicolonPartOfValue && value.indexOf(';') >= 0)
            return true;

        if (!hashPartOfValue && value.indexOf('#') >= 0)
            return true;

        return false;
    }

    /** */
    private int parseEscapeChar( char[] chars, int i, StringBuilder sb)
    {
        if( ++i >= chars.length )
        {
            /*
                Одинарная обратная косая черта в конце значения.
                Сохраняем его, а не теряйте без предупреждения.
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
                sb.append('\\');
                sb.append(escapeChar);
                break;
        }

        return i;
    }

    private boolean isCommentStart(char ch) {

        if( ch == ';')
            return !semicolonPartOfValue;

        if( ch == '#')
            return !hashPartOfValue;

        return false;
    }

    /** */
    private IllegalStateException onIllegalState( String message )
    {
        return new IllegalStateException( message + " в файле " + name + ", строка " + lineNo + ", текст: " + lineRaw );
    }

    /** */
    private static String rtrim( String value )
    {
        if( S.isNullOrEmpty(value) )
            return value;

        int end = value.length();

        while( end > 0 && Character.isWhitespace(value.charAt(end-1)))
               end--;

        if( end == value.length() )
            return value;

        return value.substring( 0, end );
    }

    /** */
    public Stream<IniFileEvent> stream()
    {
        return StreamSupport.stream( Spliterators.spliteratorUnknownSize( this, Spliterator.ORDERED | Spliterator.NONNULL ), false);
    }

    /** */
    @Override
    public void close() throws Exception {
        closed = true;
        br.close();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private Object iniFile;
        private boolean semicolonPartOfValue = true;
        private boolean hashPartOfValue = false;
        private Charset charset = WINDOWS_1251;

        public Builder iniFile(Object iniFile) {
            this.iniFile = iniFile;
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder semicolonPartOfValue(boolean value) {
            this.semicolonPartOfValue = value;
            return this;
        }

        public Builder hashPartOfValue(boolean value) {
            this.hashPartOfValue = value;
            return this;
        }

        public IniFileEventReader build()
        {
            if( iniFile == null )
                throw new IllegalArgumentException( "Необходимо задать источник данных 'iniFile'", new NullPointerException("'iniFile' is null") );

            final Path file;

            if( iniFile instanceof String )
                file = Paths.get((String) iniFile);
            else if (iniFile instanceof File)
                file = ((File) iniFile).toPath();
            else if (iniFile instanceof Path)
                file = (Path) iniFile;
            else
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Тип данных '" + iniFile.getClass().getName() + "' не может использоваться в качестве источника данных" );

            final BufferedReader br;

            try {
                br = Files.newBufferedReader( file, U.nvl(charset, WINDOWS_1251) );
            }
            catch (IOException e) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Ошибка при открытии файла: " + file, e );
            }

            return new IniFileEventReader( br, file.getFileName().toString(), semicolonPartOfValue, hashPartOfValue );
        }
    }

}