package ru.inversion.utils.parser.sql.transform;

import ru.inversion.utils.parser.lexer.LexerResult;
import ru.inversion.utils.parser.lexer.Token;
import ru.inversion.utils.parser.sql.lexer.SqlLexer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;
import ru.inversion.utils.parser.text.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Строит прикладную карту внешнего SELECT-запроса.
 *
 * <p>Структурный анализ выполняется только на нулевой
 * глубине круглых скобок.</p>
 *
 * <p>Параметры собираются на любой глубине.</p>
 */
public final class SelectQueryMapper {

    private final SqlLexer lexer;

    public SelectQueryMapper() {
        this.lexer = new SqlLexer();
    }

    public SelectQueryMap map(
            CharSequence sql
    ) {
        Objects.requireNonNull(
                sql,
                "sql"
        );

        LexerResult<SqlTokenKind> lexerResult =
                lexer.tokenize(sql);

        Structure structure =
                mapStructure(lexerResult);

        List<SqlParameterOccurrence> parameters =
                collectParameters(lexerResult);

        return new SelectQueryMap(
                structure.selectItemInsertion,
                structure.wherePredicateRange,
                structure.whereInsertion,
                structure.orderByClauseRange,
                structure.orderByInsertion,
                parameters
        );
    }

    private static Structure mapStructure(
            LexerResult<SqlTokenKind> lexerResult
    ) {
        List<Token<SqlTokenKind>> tokens =
                lexerResult.tokens();

        SourceText source =
                lexerResult.source();

        int selectIndex =
                findTopLevelSelectIndex(
                        tokens,
                        source
                );

        if (selectIndex < 0) {
            throw new IllegalArgumentException(
                    "Top-level SELECT not found"
            );
        }

        SqlAnchor selectItemInsertion =
                findSelectItemInsertion(
                        tokens,
                        source,
                        selectIndex + 1
                );

        WhereMapping whereMapping =
                mapWhere(
                        tokens,
                        source,
                        selectIndex + 1
                );

        OrderByMapping orderByMapping =
                mapOrderBy(
                        tokens,
                        source,
                        selectIndex + 1
                );

        return new Structure(
                selectItemInsertion,
                whereMapping.predicateRange,
                whereMapping.insertion,
                orderByMapping.clauseRange,
                orderByMapping.insertion
        );
    }

    /**
     * Находит первый SELECT на нулевой глубине скобок.
     */
    private static int findTopLevelSelectIndex(
            List<Token<SqlTokenKind>> tokens,
            SourceText source
    ) {
        int parenthesisDepth = 0;

        for (int index = 0;
             index < tokens.size();
             index++) {

            Token<SqlTokenKind> token =
                    tokens.get(index);

            SqlTokenKind kind =
                    token.kind();

            if (kind
                    == SqlTokenKind.LEFT_PARENTHESIS) {

                parenthesisDepth++;
                continue;
            }

            if (kind
                    == SqlTokenKind.RIGHT_PARENTHESIS) {

                if (parenthesisDepth > 0) {
                    parenthesisDepth--;
                }

                continue;
            }

            if (parenthesisDepth == 0
                    && isWord(
                    token,
                    source,
                    "select"
            )) {

                return index;
            }
        }

        return -1;
    }

    /**
     * Находит позицию перед первым элементом SELECT.
     */
    private static SqlAnchor findSelectItemInsertion(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int startIndex
    ) {
        int index =
                nextNonTriviaTokenIndex(
                        tokens,
                        startIndex
                );

        if (index >= tokens.size()) {
            return new SqlAnchor(
                    source.length()
            );
        }

        Token<SqlTokenKind> token =
                tokens.get(index);

        if (isWord(token, source, "distinct")
                || isWord(token, source, "all")) {

            index =
                    nextNonTriviaTokenIndex(
                            tokens,
                            index + 1
                    );
        }

        if (index >= tokens.size()) {
            return new SqlAnchor(
                    source.length()
            );
        }

        return new SqlAnchor(
                tokens.get(index)
                        .range()
                        .start()
        );
    }

    /**
     * Находит существующий внешний WHERE либо позицию
     * вставки нового WHERE.
     */
    private static WhereMapping mapWhere(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int startIndex
    ) {
        int parenthesisDepth = 0;

        for (int index = startIndex;
             index < tokens.size();
             index++) {

            Token<SqlTokenKind> token =
                    tokens.get(index);

            SqlTokenKind kind =
                    token.kind();

            if (kind
                    == SqlTokenKind.LEFT_PARENTHESIS) {

                parenthesisDepth++;
                continue;
            }

            if (kind
                    == SqlTokenKind.RIGHT_PARENTHESIS) {

                if (parenthesisDepth > 0) {
                    parenthesisDepth--;
                }

                continue;
            }

            if (parenthesisDepth != 0) {
                continue;
            }

            if (isWord(
                    token,
                    source,
                    "where"
            )) {
                return mapExistingWhere(
                        tokens,
                        source,
                        index
                );
            }

            if (isWhereBoundary(
                    tokens,
                    source,
                    index
            )) {
                return WhereMapping.absent(
                        token.range().start()
                );
            }
        }

        return WhereMapping.absent(
                source.length()
        );
    }

    private static WhereMapping mapExistingWhere(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int whereIndex
    ) {
        int predicateStartIndex =
                nextNonTriviaTokenIndex(
                        tokens,
                        whereIndex + 1
                );

        int boundaryIndex =
                findWhereBoundaryIndex(
                        tokens,
                        source,
                        predicateStartIndex
                );

        int boundaryOffset =
                tokens.get(boundaryIndex)
                        .range()
                        .start();

        int predicateStart;

        if (predicateStartIndex >= boundaryIndex) {
            predicateStart =
                    boundaryOffset;
        } else {
            predicateStart =
                    tokens.get(predicateStartIndex)
                            .range()
                            .start();
        }

        int predicateEnd =
                findClauseContentEnd(
                        tokens,
                        predicateStartIndex,
                        boundaryIndex,
                        boundaryOffset
                );

        return WhereMapping.present(
                new TextRange(
                        predicateStart,
                        predicateEnd
                )
        );
    }

    private static int findWhereBoundaryIndex(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int startIndex
    ) {
        int parenthesisDepth = 0;

        for (int index = startIndex;
             index < tokens.size();
             index++) {

            Token<SqlTokenKind> token =
                    tokens.get(index);

            SqlTokenKind kind =
                    token.kind();

            if (kind
                    == SqlTokenKind.LEFT_PARENTHESIS) {

                parenthesisDepth++;
                continue;
            }

            if (kind
                    == SqlTokenKind.RIGHT_PARENTHESIS) {

                if (parenthesisDepth > 0) {
                    parenthesisDepth--;
                }

                continue;
            }

            if (parenthesisDepth == 0
                    && isWhereBoundary(
                    tokens,
                    source,
                    index
            )) {

                return index;
            }
        }

        return tokens.size() - 1;
    }

    /**
     * Находит существующий внешний ORDER BY либо позицию
     * вставки нового ORDER BY.
     */
    private static OrderByMapping mapOrderBy(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int startIndex
    ) {
        int parenthesisDepth = 0;

        for (int index = startIndex;
             index < tokens.size();
             index++) {

            Token<SqlTokenKind> token =
                    tokens.get(index);

            SqlTokenKind kind =
                    token.kind();

            if (kind
                    == SqlTokenKind.LEFT_PARENTHESIS) {

                parenthesisDepth++;
                continue;
            }

            if (kind
                    == SqlTokenKind.RIGHT_PARENTHESIS) {

                if (parenthesisDepth > 0) {
                    parenthesisDepth--;
                }

                continue;
            }

            if (parenthesisDepth != 0) {
                continue;
            }

            if (isOrderBy(
                    tokens,
                    source,
                    index
            )) {
                return mapExistingOrderBy(
                        tokens,
                        source,
                        index
                );
            }

            if (isOrderByBoundary(
                    token,
                    source
            )) {
                return OrderByMapping.absent(
                        token.range().start()
                );
            }
        }

        return OrderByMapping.absent(
                source.length()
        );
    }

    private static OrderByMapping mapExistingOrderBy(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int orderIndex
    ) {
        int byIndex =
                nextNonTriviaTokenIndex(
                        tokens,
                        orderIndex + 1
                );

        int boundaryIndex =
                findOrderByBoundaryIndex(
                        tokens,
                        source,
                        byIndex + 1
                );

        int boundaryOffset =
                tokens.get(boundaryIndex)
                        .range()
                        .start();

        int clauseEnd =
                findClauseContentEnd(
                        tokens,
                        orderIndex,
                        boundaryIndex,
                        boundaryOffset
                );

        return OrderByMapping.present(
                new TextRange(
                        tokens.get(orderIndex)
                                .range()
                                .start(),
                        clauseEnd
                )
        );
    }

    private static int findOrderByBoundaryIndex(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int startIndex
    ) {
        int parenthesisDepth = 0;

        for (int index = startIndex;
             index < tokens.size();
             index++) {

            Token<SqlTokenKind> token =
                    tokens.get(index);

            SqlTokenKind kind =
                    token.kind();

            if (kind
                    == SqlTokenKind.LEFT_PARENTHESIS) {

                parenthesisDepth++;
                continue;
            }

            if (kind
                    == SqlTokenKind.RIGHT_PARENTHESIS) {

                if (parenthesisDepth > 0) {
                    parenthesisDepth--;
                }

                continue;
            }

            if (parenthesisDepth == 0
                    && isOrderByBoundary(
                    token,
                    source
            )) {

                return index;
            }
        }

        return tokens.size() - 1;
    }

    /**
     * Убирает trivia непосредственно перед следующим
     * предложением из диапазона содержимого предложения.
     */
    private static int findClauseContentEnd(
            List<Token<SqlTokenKind>> tokens,
            int contentStartIndex,
            int boundaryIndex,
            int boundaryOffset
    ) {
        int index =
                boundaryIndex - 1;

        while (index >= contentStartIndex
                && tokens.get(index)
                .kind()
                .isTrivia()) {

            index--;
        }

        if (index < contentStartIndex) {
            return boundaryOffset;
        }

        return tokens.get(index)
                .range()
                .end();
    }

    /**
     * Граница WHERE либо место вставки нового WHERE.
     */
    private static boolean isWhereBoundary(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int index
    ) {
        Token<SqlTokenKind> token =
                tokens.get(index);

        SqlTokenKind kind =
                token.kind();

        if (kind == SqlTokenKind.SEMICOLON
                || kind == SqlTokenKind.END_OF_FILE) {

            return true;
        }

        if (isWord(token, source, "having")
                || isWord(token, source, "limit")
                || isWord(token, source, "offset")
                || isWord(token, source, "fetch")
                || isWord(token, source, "for")) {

            return true;
        }

        if (isWord(token, source, "group")) {
            return isFollowingWord(
                    tokens,
                    source,
                    index,
                    "by"
            );
        }

        return isOrderBy(
                tokens,
                source,
                index
        );
    }

    /**
     * Граница содержимого ORDER BY либо место вставки
     * нового ORDER BY.
     */
    private static boolean isOrderByBoundary(
            Token<SqlTokenKind> token,
            SourceText source
    ) {
        SqlTokenKind kind =
                token.kind();

        if (kind == SqlTokenKind.SEMICOLON
                || kind == SqlTokenKind.END_OF_FILE) {

            return true;
        }

        return isWord(token, source, "limit")
                || isWord(token, source, "offset")
                || isWord(token, source, "fetch")
                || isWord(token, source, "for");
    }

    private static boolean isOrderBy(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int index
    ) {
        return isWord(
                tokens.get(index),
                source,
                "order"
        ) && isFollowingWord(
                tokens,
                source,
                index,
                "by"
        );
    }

    private static boolean isFollowingWord(
            List<Token<SqlTokenKind>> tokens,
            SourceText source,
            int index,
            String expected
    ) {
        int nextIndex =
                nextNonTriviaTokenIndex(
                        tokens,
                        index + 1
                );

        return nextIndex < tokens.size()
                && isWord(
                tokens.get(nextIndex),
                source,
                expected
        );
    }

    private static int nextNonTriviaTokenIndex(
            List<Token<SqlTokenKind>> tokens,
            int startIndex
    ) {
        int index =
                startIndex;

        while (index < tokens.size()
                && tokens.get(index)
                .kind()
                .isTrivia()) {

            index++;
        }

        return index;
    }

    private static boolean isWord(
            Token<SqlTokenKind> token,
            SourceText source,
            String expected
    ) {
        return token.kind()
                == SqlTokenKind.WORD
                && token.text(source)
                .equalsIgnoreCase(expected);
    }

    private static List<SqlParameterOccurrence>
    collectParameters(
            LexerResult<SqlTokenKind> lexerResult
    ) {
        List<SqlParameterOccurrence> result =
                new ArrayList<SqlParameterOccurrence>();

        SourceText source =
                lexerResult.source();

        for (Token<SqlTokenKind> token
                : lexerResult.tokens()) {

            if (token.kind()
                    == SqlTokenKind.JDBC_PARAMETER) {

                result.add(
                        new SqlParameterOccurrence(
                                SqlParameterKind.POSITIONAL,
                                token.range(),
                                null
                        )
                );

                continue;
            }

            if (token.kind()
                    == SqlTokenKind.NAMED_PARAMETER) {

                String parameterText =
                        token.text(source);

                result.add(
                        new SqlParameterOccurrence(
                                SqlParameterKind.NAMED,
                                token.range(),
                                parameterText.substring(1)
                        )
                );
            }
        }

        return result;
    }

    private static final class Structure {

        private final SqlAnchor selectItemInsertion;

        private final TextRange wherePredicateRange;
        private final SqlAnchor whereInsertion;

        private final TextRange orderByClauseRange;
        private final SqlAnchor orderByInsertion;

        private Structure(
                SqlAnchor selectItemInsertion,
                TextRange wherePredicateRange,
                SqlAnchor whereInsertion,
                TextRange orderByClauseRange,
                SqlAnchor orderByInsertion
        ) {
            this.selectItemInsertion =
                    selectItemInsertion;

            this.wherePredicateRange =
                    wherePredicateRange;

            this.whereInsertion =
                    whereInsertion;

            this.orderByClauseRange =
                    orderByClauseRange;

            this.orderByInsertion =
                    orderByInsertion;
        }
    }

    private static final class WhereMapping {

        private final TextRange predicateRange;
        private final SqlAnchor insertion;

        private WhereMapping(
                TextRange predicateRange,
                SqlAnchor insertion
        ) {
            this.predicateRange =
                    predicateRange;

            this.insertion =
                    insertion;
        }

        private static WhereMapping present(
                TextRange predicateRange
        ) {
            return new WhereMapping(
                    predicateRange,
                    null
            );
        }

        private static WhereMapping absent(
                int insertionOffset
        ) {
            return new WhereMapping(
                    null,
                    new SqlAnchor(insertionOffset)
            );
        }
    }

    private static final class OrderByMapping {

        private final TextRange clauseRange;
        private final SqlAnchor insertion;

        private OrderByMapping(
                TextRange clauseRange,
                SqlAnchor insertion
        ) {
            this.clauseRange =
                    clauseRange;

            this.insertion =
                    insertion;
        }

        private static OrderByMapping present(
                TextRange clauseRange
        ) {
            return new OrderByMapping(
                    clauseRange,
                    null
            );
        }

        private static OrderByMapping absent(
                int insertionOffset
        ) {
            return new OrderByMapping(
                    null,
                    new SqlAnchor(insertionOffset)
            );
        }
    }
}