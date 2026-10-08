package ru.inversion.utils.parser.lexer;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import ru.inversion.utils.parser.text.SourceText;
import ru.inversion.utils.parser.text.TextRange;

/**
 * Универсальный лексический движок.
 * <p>
 * Не содержит знаний о SQL или другом конкретном языке.
 *
 * @param <K> тип категорий токенов, например SqlTokenKind
 */
public final class LexerEngine<K extends TokenKind> {

    private static final int ERROR_CONTEXT_RADIUS = 40;

    private final List<TokenRecognizer<K>> recognizers;
    private final TokenRecognizer<K> fallbackRecognizer;

    private final K endOfFileKind;

    /**
     * @param recognizers
     *        основные правила распознавания;
     * @param fallbackRecognizer
     *        правило для неизвестного текста, обязано распознать хотя бы один символ;
     * @param endOfFileKind
     *        тип синтетического EOF-токена, может быть null, если EOF не требуется.
     */
    public LexerEngine(
        List<? extends TokenRecognizer<K>> recognizers,
        TokenRecognizer<K> fallbackRecognizer,
        K endOfFileKind
    )
    {
        Objects.requireNonNull( recognizers, "recognizers" );
        Objects.requireNonNull( fallbackRecognizer, "fallbackRecognizer" );

        List<TokenRecognizer<K>> copy = new ArrayList<TokenRecognizer<K>>( recognizers.size() );

        for( TokenRecognizer<K> recognizer : recognizers )
            copy.add( Objects.requireNonNull( recognizer, "recognizers contains null") );

        this.recognizers        = Collections.unmodifiableList(copy);
        this.fallbackRecognizer = fallbackRecognizer;
        this.endOfFileKind      = endOfFileKind;
    }

    /** Overload для CharSequence. */
    public LexerResult<K> tokenize(CharSequence text) {
        return tokenize(new SourceText(text));
    }

    /**
     * Разбивает source на непрерывный поток токенов.
     * <p>
     * Если recognizer-ы корректны, конкатенация текстов всех токенов,
     * кроме EOF, должна быть равна исходному тексту.
     */
    public LexerResult<K> tokenize(SourceText source)
    {
        Objects.requireNonNull(source, "source");

        List<Token<K>> tokens = new ArrayList<>();
        int offset = 0;

        while( offset < source.length() )
        {
            TokenMatch<K> match = findBestMatch( source, offset );
            validateMatch(source, offset, match);

            tokens.add(new Token<>( match.kind(), new TextRange(offset, match.endOffset()) ));

            offset = match.endOffset();
        }

        if( endOfFileKind != null )
            tokens.add(new Token<>( endOfFileKind, new TextRange(source.length(), source.length()) ));

        return new LexerResult<>( source, tokens );
    }

    /**
     * Ищет самое длинное совпадение.
     * <p>
     * При равной длине остаётся первое найденное правило.
     */
    private TokenMatch<K> findBestMatch( SourceText source, int offset )
    {
        TokenMatch<K> bestMatch = null;

        for( TokenRecognizer<K> recognizer : recognizers )
        {
            TokenMatch<K> candidate = invokeRecognizer( recognizer, source, offset );

            if( candidate == null )
                continue;

            validateMatch(source, offset, candidate);

            if( bestMatch == null || candidate.endOffset() > bestMatch.endOffset() )
                bestMatch = candidate;
        }

        if( bestMatch != null )
            return bestMatch;

        TokenMatch<K> fallbackMatch = invokeRecognizer(fallbackRecognizer, source, offset);

        if( fallbackMatch == null)
            throw lexerError( source, offset, "Fallback recognizer returned null" );

        validateMatch( source, offset, fallbackMatch );

        return fallbackMatch;
    }

    /**
     * Изолирует ошибки конкретного recognizer-а и добавляет контекст SQL.
     */
    private TokenMatch<K> invokeRecognizer( TokenRecognizer<K> recognizer, SourceText source, int offset )
    {
        try {
            return recognizer.match(source, offset);
        } catch (LexerException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw lexerError( source, offset, "Recognizer failed: " + recognizer.getClass().getName(), ex );
        }
    }

    /**
     * Проверяет контракт TokenRecognizer.
     */
    private void validateMatch( SourceText source, int startOffset, TokenMatch<K> match )
    {
        if( match == null )
            throw lexerError( source, startOffset, "Lexer produced no token" );

        if( match.kind() == null )
            throw lexerError( source, startOffset, "Recognizer returned null token kind" );

        if( match.endOffset() <= startOffset )
            throw lexerError( source, startOffset, "Recognizer returned an empty or backward token range: " + "[" + startOffset + ", " + match.endOffset() + ")" );

        if( match.endOffset() > source.length() )
            throw lexerError( source, startOffset, "Recognizer returned a range outside source: " + "[" + startOffset + ", " + match.endOffset() + "), source length=" + source.length() );
    }

    /** */
    private LexerException lexerError( SourceText source, int offset, String message )
    {
        return lexerError(source, offset, message, null);
    }

    /** */
    private LexerException lexerError( SourceText source, int offset, String message, Throwable cause )
    {
        int fragmentStart = Math.max( 0, offset - ERROR_CONTEXT_RADIUS );
        int fragmentEnd   = Math.min( source.length(), offset + ERROR_CONTEXT_RADIUS );

        String before = source.substring( fragmentStart, offset );
        String after  = source.substring( offset, fragmentEnd );

        String fullMessage = message + System.lineSeparator() + "Near: " + printable(before) + " >>>" + printable(after) + "<<<";

        return cause == null
                ? new LexerException(fullMessage)
                : new LexerException(fullMessage, cause);
    }

    /** */
    private static String printable(String text) {
        return text
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}