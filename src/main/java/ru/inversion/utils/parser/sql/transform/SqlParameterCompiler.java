package ru.inversion.utils.parser.sql.transform;

import ru.inversion.utils.parser.lexer.LexerResult;
import ru.inversion.utils.parser.lexer.Token;
import ru.inversion.utils.parser.sql.dialect.SqlSyntaxDialect;
import ru.inversion.utils.parser.sql.dialect.SqlSyntaxFeature;
import ru.inversion.utils.parser.sql.lexer.SqlLexer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;
import ru.inversion.utils.parser.text.TextChange;
import ru.inversion.utils.parser.text.TextChangeApplier;
import ru.inversion.utils.parser.text.TextRange;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Компилирует SQL в единый JDBC-вид.
 *
 * <p>Результирующий SQL использует только placeholder {@code ?}.
 * Одновременно строится полный список {@link SqlParameterBinding}
 * в порядке JDBC-позиций.</p>
 *
 * <p>Реализация выполняет один запуск lexer-а и один основной
 * проход по полученному списку токенов. Промежуточный SQL,
 * sentinel-параметры и повторное лексирование не используются.</p>
 *
 * <p>Поддерживаются:</p>
 *
 * <ul>
 *     <li>исходные JDBC-параметры {@code ?};</li>
 *     <li>именованные параметры {@code :name};</li>
 *     <li>PostgreSQL-параметры {@code $1}, {@code $2};</li>
 *     <li>поддерживаемые строковые и числовые литералы.</li>
 * </ul>
 *
 * <p>Защитные участки отключают только замену литералов.
 * Уже существующие параметры внутри защищённого участка
 * всё равно входят в итоговый binding plan.</p>
 */
public final class SqlParameterCompiler {

    private static final String PARAMETERIZE_OFF_DIRECTIVE =
            "/*@parameterize:off*/";

    private static final String PARAMETERIZE_ON_DIRECTIVE =
            "/*@parameterize:on*/";

    private enum ParameterizationDirective {
        OFF,
        ON
    }

    private enum ProtectionSyntax {
        NONE,
        LEGACY_TILDE,
        DIRECTIVE
    }

    private final SqlLexer lexer;
    private final SqlSyntaxDialect dialect;

    public SqlParameterCompiler(
            SqlSyntaxDialect dialect
    ) {
        this.lexer =
                new SqlLexer();

        this.dialect =
                Objects.requireNonNull(
                        dialect,
                        "dialect"
                );
    }

    /**
     * Компилирует SQL в JDBC-форму.
     *
     * @param sql исходный SQL
     *
     * @return SQL только с {@code ?} и полный binding plan
     */
    public PreparedSql compile(
            CharSequence sql
    ) {
        Objects.requireNonNull(
                sql,
                "sql"
        );

        LexerResult<SqlTokenKind> lexerResult =
                lexer.tokenize(sql);

        SourceText source =
                lexerResult.source();

        List<Token<SqlTokenKind>> tokens =
                lexerResult.tokens();

        List<TextChange> changes =
                new ArrayList<TextChange>();

        List<SqlParameterBinding> bindings =
                new ArrayList<SqlParameterBinding>();

        ProtectionSyntax protectionSyntax =
                ProtectionSyntax.NONE;

        boolean protectionActive =
                false;

        int protectionStartOffset =
                -1;

        int jdbcPosition =
                0;

        int jdbcSourcePosition =
                0;

        /*
         * Храним предыдущий non-trivia token во время основного
         * прохода. Это исключает обратный поиск по tokens для
         * каждого строкового литерала.
         */
        int previousNonTriviaIndex =
                -1;

        for (int index = 0;
             index < tokens.size();
             index++) {

            Token<SqlTokenKind> token =
                    tokens.get(index);

            SqlTokenKind kind =
                    token.kind();

            if (kind
                    == SqlTokenKind
                    .PREPROCESSOR_DIRECTIVE) {

                if (protectionSyntax
                        == ProtectionSyntax
                        .LEGACY_TILDE) {

                    throw mixedProtectionSyntax(
                            token.range().start()
                    );
                }

                protectionSyntax =
                        ProtectionSyntax.DIRECTIVE;

                ParameterizationDirective directive =
                        parseParameterizationDirective(
                                token.text(source),
                                token.range().start()
                        );

                if (directive
                        == ParameterizationDirective.OFF) {

                    if (protectionActive) {
                        throw nestedParameterizationOff(
                                token.range().start()
                        );
                    }

                    protectionActive =
                            true;

                    protectionStartOffset =
                            token.range().start();
                } else {
                    if (!protectionActive) {
                        throw unexpectedParameterizationOn(
                                token.range().start()
                        );
                    }

                    protectionActive =
                            false;

                    protectionStartOffset =
                            -1;
                }

                /*
                 * PREPROCESSOR_DIRECTIVE является trivia,
                 * поэтому previousNonTriviaIndex не меняется.
                 */
                continue;
            }

            if (isLegacyParameterizationMarker(
                    token,
                    source
            )) {
                if (protectionSyntax
                        == ProtectionSyntax.DIRECTIVE) {

                    throw mixedProtectionSyntax(
                            token.range().start()
                    );
                }

                protectionSyntax =
                        ProtectionSyntax.LEGACY_TILDE;

                changes.add(
                        new TextChange(
                                token.range(),
                                ""
                        )
                );

                if (!protectionActive) {
                    protectionActive =
                            true;

                    protectionStartOffset =
                            token.range().start();
                } else {
                    protectionActive =
                            false;

                    protectionStartOffset =
                            -1;
                }

                previousNonTriviaIndex =
                        index;

                continue;
            }

            /*
             * Существующие параметры компилируются независимо
             * от состояния защиты. Защита влияет только
             * на автоматическую замену SQL-литералов.
             */
            switch (kind) {
                case JDBC_PARAMETER:
                    jdbcPosition++;
                    jdbcSourcePosition++;

                    bindings.add(
                            SqlParameterBinding
                                    .jdbcPositional(
                                            jdbcPosition,
                                            jdbcSourcePosition
                                    )
                    );

                    previousNonTriviaIndex =
                            index;

                    continue;

                case NAMED_PARAMETER:
                    jdbcPosition++;

                    bindings.add(
                            SqlParameterBinding.named(
                                    jdbcPosition,
                                    parameterName(
                                            token,
                                            source
                                    )
                            )
                    );

                    changes.add(
                            jdbcReplacement(token)
                    );

                    previousNonTriviaIndex =
                            index;

                    continue;

                case POSTGRES_POSITIONAL_PARAMETER:
                    jdbcPosition++;

                    bindings.add(
                            SqlParameterBinding
                                    .numberedPositional(
                                            jdbcPosition,
                                            postgresSourcePosition(
                                                    token,
                                                    source
                                            )
                                    )
                    );

                    changes.add(
                            jdbcReplacement(token)
                    );

                    previousNonTriviaIndex =
                            index;

                    continue;

                default:
                    break;
            }

            if (!protectionActive) {
                Object generatedValue =
                        generatedLiteralValue(
                                tokens,
                                index,
                                previousNonTriviaIndex,
                                source,
                                token
                        );

                if (generatedValue
                        != NoGeneratedLiteral.VALUE) {

                    jdbcPosition++;

                    bindings.add(
                            SqlParameterBinding
                                    .generatedLiteral(
                                            jdbcPosition,
                                            generatedValue
                                    )
                    );

                    changes.add(
                            jdbcReplacement(token)
                    );
                }
            }

            if (!kind.isTrivia()
                    && !kind.isEndOfFile()) {

                previousNonTriviaIndex =
                        index;
            }
        }

        verifyProtectionClosed(
                protectionSyntax,
                protectionActive,
                protectionStartOffset
        );

        String jdbcSql =
                TextChangeApplier.apply(
                        sql,
                        changes
                );

        return new PreparedSql(
                jdbcSql,
                bindings
        );
    }

    private Object generatedLiteralValue(
            List<Token<SqlTokenKind>> tokens,
            int tokenIndex,
            int previousNonTriviaIndex,
            SourceText source,
            Token<SqlTokenKind> token
    ) {
        SqlTokenKind kind =
                token.kind();

        switch (kind) {
            case STRING_LITERAL:
                if (!shouldParameterizeStringLiteral(
                        tokens,
                        tokenIndex,
                        previousNonTriviaIndex,
                        source
                )) {
                    return NoGeneratedLiteral.VALUE;
                }

                return parseStringLiteral(
                        token.text(source),
                        token.range().start()
                );

            case POSTGRES_DOLLAR_QUOTED_STRING:
                if (!dialect.supports(
                        SqlSyntaxFeature
                                .POSTGRES_DOLLAR_QUOTED_STRING
                )) {
                    return NoGeneratedLiteral.VALUE;
                }

                return parsePostgresDollarQuotedString(
                        token.text(source),
                        token.range().start()
                );

            case ORACLE_Q_QUOTED_STRING:
                if (!dialect.supports(
                        SqlSyntaxFeature
                                .ORACLE_Q_QUOTED_STRING
                )) {
                    return NoGeneratedLiteral.VALUE;
                }

                return parseOracleQQuotedString(
                        token.text(source),
                        token.range().start()
                );

            case INTEGER_LITERAL:
                if (!isStandaloneNumericLiteral(
                        source,
                        token.range()
                )) {
                    return NoGeneratedLiteral.VALUE;
                }

                return parseIntegerLiteral(
                        token.text(source),
                        token.range().start()
                );

            case DECIMAL_LITERAL:
                if (!isStandaloneNumericLiteral(
                        source,
                        token.range()
                )) {
                    return NoGeneratedLiteral.VALUE;
                }

                return parseDecimalLiteral(
                        token.text(source),
                        token.range().start()
                );

            default:
                return NoGeneratedLiteral.VALUE;
        }
    }

    private static boolean shouldParameterizeStringLiteral(
            List<Token<SqlTokenKind>> tokens,
            int tokenIndex,
            int previousNonTriviaIndex,
            SourceText source
    ) {
        Token<SqlTokenKind> token =
                tokens.get(tokenIndex);

        int start =
                token.range().start();

        if (hasAdjacentStringPrefix(
                source,
                start
        )) {
            return false;
        }

        if (previousNonTriviaIndex < 0) {
            return true;
        }

        Token<SqlTokenKind> previous =
                tokens.get(
                        previousNonTriviaIndex
                );

        if (previous.kind()
                != SqlTokenKind.WORD) {

            return true;
        }

        String previousWord =
                previous.text(source)
                        .toLowerCase(Locale.ROOT);

        return !isTypedLiteralPrefix(
                previousWord
        );
    }

    private static boolean hasAdjacentStringPrefix(
            SourceText source,
            int stringStart
    ) {
        int previousCharacter =
                source.get(stringStart - 1);

        if (previousCharacter
                == SourceText.EOF) {

            return false;
        }

        char value =
                (char) previousCharacter;

        return Character.isLetterOrDigit(value)
                || value == '_'
                || value == '$'
                || value == '&';
    }

    private static boolean isTypedLiteralPrefix(
            String word
    ) {
        return "date".equals(word)
                || "time".equals(word)
                || "timestamp".equals(word)
                || "interval".equals(word)
                || "bit".equals(word)
                || "varbit".equals(word)
                || "national".equals(word)
                || "nchar".equals(word)
                || "character".equals(word)
                || "char".equals(word)
                || "varchar".equals(word)
                || "varchar2".equals(word)
                || "nvarchar2".equals(word)
                || "raw".equals(word)
                || "json".equals(word)
                || "jsonb".equals(word)
                || "xml".equals(word)
                || "uuid".equals(word)
                || "zone".equals(word);
    }

    private static boolean isStandaloneNumericLiteral(
            SourceText source,
            TextRange range
    ) {
        int previousCharacter =
                source.get(
                        range.start() - 1
                );

        int nextCharacter =
                source.get(
                        range.end()
                );

        return !isNumericNeighbor(
                previousCharacter
        ) && !isNumericNeighbor(
                nextCharacter
        );
    }

    private static boolean isNumericNeighbor(
            int character
    ) {
        if (character
                == SourceText.EOF) {

            return false;
        }

        char value =
                (char) character;

        return Character.isLetterOrDigit(value)
                || value == '_'
                || value == '$'
                || value == ':';
    }

    private static String parseStringLiteral(
            String literalText,
            int offset
    ) {
        if (literalText.length() < 2
                || literalText.charAt(0) != '\''
                || literalText.charAt(
                literalText.length() - 1
        ) != '\'') {

            throw new IllegalArgumentException(
                    "Unterminated string literal at offset "
                            + offset
            );
        }

        String content =
                literalText.substring(
                        1,
                        literalText.length() - 1
                );

        return content.replace(
                "''",
                "'"
        );
    }

    private static String parsePostgresDollarQuotedString(
            String literalText,
            int offset
    ) {
        int openingDelimiterEnd =
                literalText.indexOf(
                        '$',
                        1
                );

        if (openingDelimiterEnd < 1) {
            throw unterminatedPostgresString(
                    offset
            );
        }

        String delimiter =
                literalText.substring(
                        0,
                        openingDelimiterEnd + 1
                );

        int delimiterLength =
                delimiter.length();

        if (literalText.length()
                < delimiterLength * 2
                || !literalText.endsWith(delimiter)) {

            throw unterminatedPostgresString(
                    offset
            );
        }

        return literalText.substring(
                delimiterLength,
                literalText.length()
                        - delimiterLength
        );
    }

    private static String parseOracleQQuotedString(
            String literalText,
            int offset
    ) {
        if (literalText.length() < 5
                || !isOracleQ(
                literalText.charAt(0)
        )
                || literalText.charAt(1) != '\'') {

            throw unterminatedOracleString(
                    offset
            );
        }

        char openingDelimiter =
                literalText.charAt(2);

        char closingDelimiter =
                oracleClosingDelimiter(
                        openingDelimiter
                );

        int closingDelimiterIndex =
                literalText.length() - 2;

        if (literalText.charAt(
                closingDelimiterIndex
        ) != closingDelimiter
                || literalText.charAt(
                literalText.length() - 1
        ) != '\'') {

            throw unterminatedOracleString(
                    offset
            );
        }

        return literalText.substring(
                3,
                closingDelimiterIndex
        );
    }

    private static boolean isOracleQ(
            char character
    ) {
        return character == 'q'
                || character == 'Q';
    }

    private static char oracleClosingDelimiter(
            char openingDelimiter
    ) {
        switch (openingDelimiter) {
            case '[':
                return ']';

            case '{':
                return '}';

            case '(':
                return ')';

            case '<':
                return '>';

            default:
                return openingDelimiter;
        }
    }

    private static BigInteger parseIntegerLiteral(
            String literalText,
            int offset
    ) {
        try {
            return new BigInteger(
                    literalText
            );
        } catch (NumberFormatException exception) {
            throw invalidNumericLiteral(
                    literalText,
                    offset,
                    exception
            );
        }
    }

    private static BigDecimal parseDecimalLiteral(
            String literalText,
            int offset
    ) {
        try {
            return new BigDecimal(
                    literalText
            );
        } catch (NumberFormatException exception) {
            throw invalidNumericLiteral(
                    literalText,
                    offset,
                    exception
            );
        }
    }

    private static String parameterName(
            Token<SqlTokenKind> token,
            SourceText source
    ) {
        String text =
                token.text(source);

        return text.substring(1);
    }

    private static int postgresSourcePosition(
            Token<SqlTokenKind> token,
            SourceText source
    ) {
        String text =
                token.text(source);

        String numberText =
                text.substring(1);

        int position;

        try {
            position =
                    Integer.parseInt(
                            numberText
                    );
        } catch (NumberFormatException exception) {
            throw invalidPostgresPosition(
                    text,
                    token.range().start(),
                    exception
            );
        }

        if (position < 1) {
            throw invalidPostgresPosition(
                    text,
                    token.range().start(),
                    null
            );
        }

        return position;
    }

    private static TextChange jdbcReplacement(
            Token<SqlTokenKind> token
    ) {
        return new TextChange(
                token.range(),
                "?"
        );
    }

    private static ParameterizationDirective
    parseParameterizationDirective(
            String directiveText,
            int offset
    ) {
        String normalized =
                normalizeDirective(
                        directiveText
                );

        if (PARAMETERIZE_OFF_DIRECTIVE.equals(
                normalized
        )) {
            return ParameterizationDirective.OFF;
        }

        if (PARAMETERIZE_ON_DIRECTIVE.equals(
                normalized
        )) {
            return ParameterizationDirective.ON;
        }

        throw new IllegalStateException(
                "Unsupported preprocessor directive "
                        + "at offset "
                        + offset
        );
    }

    private static String normalizeDirective(
            String directiveText
    ) {
        StringBuilder normalized =
                new StringBuilder(
                        directiveText.length()
                );

        for (int index = 0;
             index < directiveText.length();
             index++) {

            char character =
                    directiveText.charAt(index);

            if (Character.isWhitespace(character)
                    || Character.isSpaceChar(character)) {

                continue;
            }

            normalized.append(
                    toLowerAscii(character)
            );
        }

        return normalized.toString();
    }

    private static char toLowerAscii(
            char character
    ) {
        if (character >= 'A'
                && character <= 'Z') {

            return (char) (
                    character
                            + ('a' - 'A')
            );
        }

        return character;
    }

    private static boolean isLegacyParameterizationMarker(
            Token<SqlTokenKind> token,
            SourceText source
    ) {
        return token.kind()
                == SqlTokenKind.OPERATOR
                && "~".equals(
                token.text(source)
        );
    }

    private static void verifyProtectionClosed(
            ProtectionSyntax syntax,
            boolean active,
            int startOffset
    ) {
        if (!active) {
            return;
        }

        if (syntax
                == ProtectionSyntax.DIRECTIVE) {

            throw unclosedParameterizationOff(
                    startOffset
            );
        }

        if (syntax
                == ProtectionSyntax.LEGACY_TILDE) {

            throw unclosedLegacyProtection(
                    startOffset
            );
        }

        throw new IllegalStateException(
                "Active parameterization protection "
                        + "without protection syntax"
        );
    }

    private static IllegalArgumentException
    invalidPostgresPosition(
            String parameterText,
            int offset,
            NumberFormatException cause
    ) {
        String message =
                "Invalid PostgreSQL positional parameter '"
                        + parameterText
                        + "' at offset "
                        + offset;

        return cause == null
                ? new IllegalArgumentException(
                message
        )
                : new IllegalArgumentException(
                message,
                cause
        );
    }

    private static IllegalArgumentException
    invalidNumericLiteral(
            String literalText,
            int offset,
            NumberFormatException cause
    ) {
        return new IllegalArgumentException(
                "Invalid numeric literal '"
                        + literalText
                        + "' at offset "
                        + offset,
                cause
        );
    }

    private static IllegalArgumentException
    unterminatedPostgresString(
            int offset
    ) {
        return new IllegalArgumentException(
                "Unterminated PostgreSQL "
                        + "dollar-quoted string at offset "
                        + offset
        );
    }

    private static IllegalArgumentException
    unterminatedOracleString(
            int offset
    ) {
        return new IllegalArgumentException(
                "Unterminated Oracle "
                        + "q-quoted string at offset "
                        + offset
        );
    }

    private static IllegalStateException
    nestedParameterizationOff(
            int offset
    ) {
        return new IllegalStateException(
                "Nested @parameterize:off "
                        + "directive at offset "
                        + offset
        );
    }

    private static IllegalStateException
    unexpectedParameterizationOn(
            int offset
    ) {
        return new IllegalStateException(
                "Unexpected @parameterize:on "
                        + "directive at offset "
                        + offset
        );
    }

    private static IllegalStateException
    unclosedParameterizationOff(
            int offset
    ) {
        return new IllegalStateException(
                "Unclosed @parameterize:off "
                        + "directive at offset "
                        + offset
        );
    }

    private static IllegalStateException
    unclosedLegacyProtection(
            int offset
    ) {
        return new IllegalStateException(
                "Unclosed legacy parameterization "
                        + "marker at offset "
                        + offset
        );
    }

    private static IllegalStateException
    mixedProtectionSyntax(
            int offset
    ) {
        return new IllegalStateException(
                "Legacy '~' markers and "
                        + "@parameterize directives "
                        + "cannot be mixed in one SQL "
                        + "at offset "
                        + offset
        );
    }

    /**
     * Специальный sentinel-объект результата helper-а.
     * Он не попадает в binding plan и не связан
     * с SQL sentinel-параметрами старой реализации.
     */
    private enum NoGeneratedLiteral {
        VALUE
    }
}
