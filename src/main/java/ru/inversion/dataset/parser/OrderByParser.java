package ru.inversion.dataset.parser;

import ru.inversion.utils.S;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Splits an ORDER BY expression by top-level commas. */
public final class OrderByParser {

    private OrderByParser() {
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
        int itemStart = 0;
        int parenthesisDepth = 0;

        for( int i = 0; i < expression.length(); i++ ) {
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
