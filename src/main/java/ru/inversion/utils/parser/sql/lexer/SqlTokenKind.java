package ru.inversion.utils.parser.sql.lexer;

import ru.inversion.utils.parser.lexer.TokenKind;

/**
 * Лексические категории SQL.
 *
 * Здесь нет грамматических сущностей SELECT, FROM, WHERE и ORDER BY.
 * Обычные слова возвращаются как WORD. Их грамматическое значение
 * определяет parser с учётом контекста и SQL-диалекта.
 */
public enum SqlTokenKind implements TokenKind {

    /**
     * Синтетический токен конца текста.
     * Его диапазон всегда [source.length(), source.length()).
     */
    END_OF_FILE(false, false, false),

    /**
     * Неизвестный или пока не поддерживаемый символ/фрагмент.
     */
    UNKNOWN(false, false, false),

    /*
     * Trivia.
     *
     * Эти токены сохраняются в общем потоке, чтобы lexer был lossless,
     * но обычный SQL parser их пропускает.
     */
    WHITESPACE(true, false, false),
    LINE_COMMENT(true, false, false),
    BLOCK_COMMENT(true, false, false),

    OPTIMIZER_HINT(true, false, false),

    /**
     * Внутренняя директива препроцессора:
     *
     * <pre>
     * /* @parameterize:off *&#47;
     * /* @parameterize:on  *&#47;
     * </pre>
     *
     * Для SQL parser является trivia, но обрабатывается
     * отдельным проходом.
     */
    PREPROCESSOR_DIRECTIVE(true, false, false),

    /**
     * Последовательность символов обычного SQL-слова.
     *
     * Примеры:
     *   select
     *   from
     *   customer
     *   column_name
     *   user
     *
     * Является ли WORD ключевым словом или идентификатором,
     * определяет parser с учётом позиции и SQL dialect.
     */
    WORD(false, false, false),

    /**
     * Идентификатор в двойных кавычках.
     *
     * Примеры:
     *   "Customer"
     *   "select"
     *   "Column Name"
     */
    QUOTED_IDENTIFIER(false, false, false),

    /*
     * Литералы.
     */
    INTEGER_LITERAL(false, true, false),
    DECIMAL_LITERAL(false, true, false),

    /**
     * Строка в одинарных кавычках:
     *
     *   'text'
     *   'a''b'
     */
    STRING_LITERAL(false, true, false),

    /**
     * PostgreSQL dollar-quoted string:
     *
     *   $$text$$
     *   $tag$text$tag$
     */
    POSTGRES_DOLLAR_QUOTED_STRING(false, true, false),

    /**
     * Oracle q-quoted string:
     *
     *   q'[text]'
     *   q'{text}'
     */
    ORACLE_Q_QUOTED_STRING(false, true, false),

    /*
     * Параметры.
     */
    JDBC_PARAMETER(false, false, true),                 // ?
    NAMED_PARAMETER(false, false, true),                // :name
    POSTGRES_POSITIONAL_PARAMETER(false, false, true),  // $1

    /*
     * Разделители.
     */
    LEFT_PARENTHESIS(false, false, false),   // (
    RIGHT_PARENTHESIS(false, false, false),  // )

    LEFT_BRACKET(false, false, false),       // [
    RIGHT_BRACKET(false, false, false),      // ]

    LEFT_BRACE(false, false, false),         // {
    RIGHT_BRACE(false, false, false),        // }

    COMMA(false, false, false),              // ,
    DOT(false, false, false),                // .
    SEMICOLON(false, false, false),          // ;

    /**
     * Оператор.
     *
     * Примеры:
     *
     *   +
     *   -
     *   *
     *   /
     *   =
     *   <=
     *   >=
     *   <>
     *   !=
     *   ||
     *   ::
     *   :=
     *   ->
     *   ->>
     */
    OPERATOR(false, false, false);

    private final boolean trivia;
    private final boolean literal;
    private final boolean parameter;

    SqlTokenKind(
            boolean trivia,
            boolean literal,
            boolean parameter
    ) {
        this.trivia = trivia;
        this.literal = literal;
        this.parameter = parameter;
    }

    /**
     * Токен не участвует непосредственно в SQL-грамматике.
     *
     * Он сохраняется для точного восстановления исходного текста.
     */
    public boolean isTrivia() {
        return trivia;
    }

    /**
     * Токен представляет SQL literal.
     */
    public boolean isLiteral() {
        return literal;
    }

    /**
     * Токен представляет уже существующий параметр.
     */
    public boolean isParameter() {
        return parameter;
    }

    /**
     * Внутренняя директива препроцессора.
     */
    public boolean isDirective() {
        return this == PREPROCESSOR_DIRECTIVE;
    }

    /**
     * Синтетический конец входного текста.
     */
    public boolean isEndOfFile() {
        return this == END_OF_FILE;
    }
}