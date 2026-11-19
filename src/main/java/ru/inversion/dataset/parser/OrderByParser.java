package ru.inversion.dataset.parser;

import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Разбор элементов ORDER BY и его выделение из SQL-фильтра. */
public final class OrderByParser {

    private OrderByParser() {
    }

    /**
     * Выделяет ORDER BY верхнего уровня из SQL-фильтра без пересборки текста.
     * Результат: [предикат, выражение сортировки]; второй элемент равен null,
     * если сортировки в фильтре нет.
     * Завершающий -- комментарий оканчивается  переводом строки, чтобы следующий
     * добавленный фрагмент не попал в него.
     */
    public static String[] splitFilter(String filter, SqlScanner scanner)
    {
        Checks.Require.text  ( filter, "filter"  );
        Checks.Require.object( scanner, "scanner");

        List<SqlScanner.Token> tokens = scanner.scan(filter);

        int depth = 0;

        for (int i = 0; i < tokens.size(); i++)
        {
            SqlScanner.Token token = tokens.get(i);

            if( token.kind() == SqlScanner.Kind.SYMBOL)
            {
                String symbol = token.text(filter);

                if ("(".equals(symbol))
                    depth++;

                else if (")".equals(symbol) && depth > 0)
                    depth--;
            }

            if( depth != 0 || !token.isWord(filter, "ORDER") )
                continue;

            int by = i + 1;

            while(  by < tokens.size() && (tokens.get(by).kind() == SqlScanner.Kind.WHITESPACE || tokens.get(by).kind() == SqlScanner.Kind.COMMENT))
                by++;

            if( by < tokens.size() && tokens.get(by).isWord(filter, "BY"))
            {
                int predicateEnd = token.start();
                for (int previous = i - 1; previous >= 0; previous--)
                {
                    SqlScanner.Token tail = tokens.get(previous);
                    if (tail.kind() == SqlScanner.Kind.WHITESPACE)
                        continue;
                    // Удаляем завершающий -- комментарий: trim() уберёт
                    // перевод строки, и комментарий поглотит скобку WHERE.
                    if (tail.kind() == SqlScanner.Kind.COMMENT
                            && tail.text(filter).startsWith("--")) {
                        predicateEnd = tail.start();
                        continue;
                    }
                    break;
                }

                String orderBy = filter.substring(tokens.get(by).end()).trim();
                for (int last = tokens.size() - 1; last > by; last--) {
                    SqlScanner.Token tail = tokens.get(last);
                    if (tail.kind() == SqlScanner.Kind.WHITESPACE)
                        continue;
                    // После однострочного комментария может добавляться SQL.
                    if (tail.kind() == SqlScanner.Kind.COMMENT
                            && tail.text(filter).startsWith("--"))
                        orderBy += "\n";
                    break;
                }
                return new String[] {
                        filter.substring(0, predicateEnd).trim(),
                        orderBy
                };
            }
        }

        return new String[] {filter, null};
    }

    /**
     * Returns ORDER BY items without interpreting their SQL expressions.
     * Commas inside parentheses, quoted text and comments are preserved.
     */
    public static List<String> parseAndGetColumnsList(String expression) {

        if( S.isNullOrEmpty(expression) )
            return Collections.emptyList();

        if( expression.indexOf(',') < 0 )
            return Collections.singletonList(expression);

        List<String> result = new ArrayList<>();
        int itemStart        = 0;
        int parenthesisDepth = 0;

        for( int i = 0; i < expression.length(); i++ )
        {
            char current = expression.charAt(i);

            if( current == '\'' || current == '"' ) {
                i = quotedEnd(expression, i, current);
                continue;
            }

            if( current == '-' && follows(expression, i, '-') ) {
                i = lineCommentEnd(expression, i + 2);
                continue;
            }

            if( current == '/' && follows(expression, i, '*') ) {
                i = blockCommentEnd(expression, i + 2);
                continue;
            }

            if( current == '(' ) {
                parenthesisDepth++;
                continue;
            }

            if( current == ')' ) {
                if( parenthesisDepth > 0 )
                    parenthesisDepth--;
                continue;
            }

            if( current == ',' && parenthesisDepth == 0 ) {
                result.add(expression.substring(itemStart, i).trim());
                itemStart = i + 1;
            }
        }

        result.add(expression.substring(itemStart).trim());

        return result;
    }

    private static int quotedEnd(String expression, int opening, char quote) {
        for( int i = opening + 1; i < expression.length(); i++ ) {
            if( expression.charAt(i) != quote )
                continue;

            if( follows(expression, i, quote) ) {
                i++;
                continue;
            }

            return i;
        }

        return expression.length() - 1;
    }

    private static int lineCommentEnd(String expression, int start) {
        for( int i = start; i < expression.length(); i++ ) {
            char current = expression.charAt(i);
            if( current == '\n' || current == '\r' )
                return i;
        }

        return expression.length() - 1;
    }

    private static int blockCommentEnd(String expression, int start) {
        for( int i = start; i + 1 < expression.length(); i++ ) {
            if( expression.charAt(i) == '*'
                    && expression.charAt(i + 1) == '/' )
                return i + 1;
        }

        return expression.length() - 1;
    }

    private static boolean follows(String expression, int at, char expected) {
        return at + 1 < expression.length()
                && expression.charAt(at + 1) == expected;
    }
}
