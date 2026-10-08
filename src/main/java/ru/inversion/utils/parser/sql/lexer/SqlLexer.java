package ru.inversion.utils.parser.sql.lexer;

import ru.inversion.utils.parser.lexer.LexerEngine;
import ru.inversion.utils.parser.lexer.LexerResult;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.lexer.recognizer.FixedTextRecognizer;
import ru.inversion.utils.parser.lexer.recognizer.SingleCharacterRecognizer;
import ru.inversion.utils.parser.lexer.recognizer.WhitespaceRecognizer;
import ru.inversion.utils.parser.sql.lexer.recognizer.*;

import java.util.Arrays;
import java.util.List;

/**
 * Публичная точка входа SQL lexer-а.
 */
public final class SqlLexer {

    private final LexerEngine<SqlTokenKind> engine;

    public SqlLexer() {

        List<TokenRecognizer<SqlTokenKind>> recognizers = Arrays.asList(

            new WhitespaceRecognizer<>( SqlTokenKind.WHITESPACE ),

            new LineCommentRecognizer(),

            new PreprocessorDirectiveRecognizer(),
            new OptimizerHintRecognizer(),
            new BlockCommentRecognizer(),

            new OracleQQuotedStringRecognizer(),
            new StringLiteralRecognizer(),
            new QuotedIdentifierRecognizer(),

            new PostgresDollarQuotedStringRecognizer(),

            new JdbcParameterRecognizer(),
            new NamedParameterRecognizer(),
            new PostgresPositionalParameterRecognizer(),

            new NumberRecognizer(),
            new WordRecognizer(),

            new OperatorRecognizer(
                /*
                 * Операторы из трёх символов.
                 */
                "->>",
                "#>>",
                "!~*",

                /*
                 * Операторы из двух символов.
                 */
                "<=",
                ">=",
                "<>",
                "!=",
                "||",
                "&&",
                "<<",
                ">>",
                "::",
                ":=",
                "->",
                "#>",
                "#-",
                "@>",
                "<@",
                "~*",
                "!~",
                "@@",
                "@?",
                "!<",
                "!>",

                /*
                 * Операторы из одного символа.
                 */
                "+",
                "-",
                "*",
                "/",
                "%",
                "=",
                "<",
                ">",
                "&",
                "|",
                "^",
                "~"
            ),

            fixed("(", SqlTokenKind.LEFT_PARENTHESIS),
            fixed(")", SqlTokenKind.RIGHT_PARENTHESIS),

            fixed("[", SqlTokenKind.LEFT_BRACKET),
            fixed("]", SqlTokenKind.RIGHT_BRACKET),

            fixed("{", SqlTokenKind.LEFT_BRACE),
            fixed("}", SqlTokenKind.RIGHT_BRACE),

            fixed(",", SqlTokenKind.COMMA),
            fixed(".", SqlTokenKind.DOT),
            fixed(";", SqlTokenKind.SEMICOLON)
        );

        this.engine = new LexerEngine<>( recognizers, new SingleCharacterRecognizer<>( SqlTokenKind.UNKNOWN ), SqlTokenKind.END_OF_FILE );
    }

    public LexerResult<SqlTokenKind> tokenize( CharSequence source )
    {
        return engine.tokenize(source);
    }

    private static TokenRecognizer<SqlTokenKind> fixed( String text, SqlTokenKind kind )
    {
        return new FixedTextRecognizer<>( text, kind );
    }
}