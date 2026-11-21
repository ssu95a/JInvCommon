package ru.inversion.datacall;

import ru.inversion.dataset.parser.lexer.SqlScanner.Token;
import ru.inversion.utils.converter.TypeConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;

import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.*;

/** Подготовка SQL вызова заменой отдельных диапазонов исходного текста. */
final class CallPrepareContext {
    private final String source;
    private final List<Token> tokens;
    private final boolean removeAdditionalInfo;
    private final StringBuilder sql;

    CallPrepareContext(String source, List<Token> tokens, boolean removeAdditionalInfo) {
        this.source = source;
        this.tokens = tokens;
        this.removeAdditionalInfo = removeAdditionalInfo;
        sql = new StringBuilder(source.length());
    }

    String prepareJdbc(boolean replaceConstants, boolean textOnly,
                       BiConsumer<Integer, Comparable<?>> onBind, BiConsumer<Integer, Object> onLiteral) {
        int parameterPosition = 0;
        int indexedBind = 0;
        int depth = 0;
        int orderByDepth = -1;
        boolean replaceHere = replaceConstants;

        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (isUnchangedMark(token)) {
                replaceHere = replaceConstants && !replaceHere;
                appendGap(token);
                continue;
            }

            if (replaceConstants) {
                if (isSymbol(token, '(')) {
                    depth++;
                } else if (isSymbol(token, ')')) {
                    if (depth == orderByDepth)
                        orderByDepth = -1;
                    depth--;
                } else if (isSymbol(token, ';')) {
                    orderByDepth = -1;
                } else if (orderByDepth < 0 && token.isWord(source, "ORDER") && isFollowedByBy(index)) {
                    orderByDepth = depth;
                }
            }

            if (isBind(token)) {
                Comparable<?> id = bindId(token, indexedBind);
                if (token.kind() == POSITIONAL_PARAMETER)
                    indexedBind++;
                parameterPosition++;
                if (!textOnly)
                    onBind.accept(parameterPosition, id);
                appendBind("?", parameterPosition, id);
            } else if (replaceHere && orderByDepth < 0 && isSimpleLiteral(token)) {
                parameterPosition++;
                String text = token.text(source);
                if (!textOnly)
                    onLiteral.accept(parameterPosition, literalValue(token, text));
                sql.append('?');
                if (!removeAdditionalInfo)
                    appendAnnotation(parameterPosition, textOnly ? text : ":" + text);
            } else {
                appendSource(token);
            }
        }
        return sql.toString();
    }

    String rewriteBinds(BiConsumer<Comparable<?>, CallPrepareContext> rewrite) {
        int indexedBind = 0;
        for (Token token : tokens) {
            if (rewrite != null && isBind(token)) {
                Comparable<?> id = bindId(token, indexedBind);
                if (token.kind() == POSITIONAL_PARAMETER)
                    indexedBind++;
                rewrite.accept(id, this);
            } else {
                appendSource(token);
            }
        }
        return sql.toString();
    }

    void appendBind(String replacement, int annotationPosition, Comparable<?> id) {
        sql.append(replacement);
        if (!removeAdditionalInfo && annotationPosition > 0)
            appendAnnotation(annotationPosition, id instanceof Integer ? ":?" : ":" + id);
    }

    private void appendAnnotation(int position, String text) {
        // Разделители внутри литерала не должны закрывать или вкладывать комментарий.
        sql.append(" /* ").append(position).append(", ")
                .append(text.replace("/*", "/ *").replace("*/", "* /")).append(" */ ");
    }

    private boolean isFollowedByBy(int index) {
        for (int next = index + 1; next < tokens.size(); next++) {
            Token token = tokens.get(next);
            if (token.kind() != WHITESPACE && token.kind() != HINT && !isComment(token))
                return token.isWord(source, "BY");
        }
        return false;
    }

    private boolean isUnchangedMark(Token token) {
        return (token.kind() == SYMBOL || token.kind() == OPERATOR)
                && token.end() - token.start() == 1 && source.charAt(token.start()) == '~';
    }

    private boolean isSymbol(Token token, char symbol) {
        return token.kind() == SYMBOL && token.end() - token.start() == 1 && source.charAt(token.start()) == symbol;
    }

    private static boolean isBind(Token token) {
        return token.kind() == NAMED_PARAMETER || token.kind() == POSITIONAL_PARAMETER;
    }

    private Comparable<?> bindId(Token token, int indexedBind) {
        return token.kind() == NAMED_PARAMETER ? source.substring(token.start() + 1, token.end()) : indexedBind;
    }

    private boolean isSimpleLiteral(Token token) {
        return token.kind() == NUMBER || (token.kind() == STRING && source.charAt(token.start()) == '\'');
    }

    private static Object literalValue(Token token, String text) {
        if (token.kind() == STRING)
            return text.substring(1, text.length() - 1).replace("''", "'");
        if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0)
            return new BigDecimal(text);
        return TypeConverter.convert(text, Number.class);
    }

    private static boolean isComment(Token token) {
        return token.kind() == COMMENT || token.kind() == PARAMETERIZE_OFF || token.kind() == PARAMETERIZE_ON;
    }

    private void appendSource(Token token) {
        if (isUnchangedMark(token) || (removeAdditionalInfo && isComment(token)))
            appendGap(token);
        else
            sql.append(source, token.start(), token.end());
    }

    private void appendGap(Token token) {
        // При удалении комментариев сохраняем разделение лексем и переводы строк.
        int start = sql.length();
        for (int at = token.start(); at < token.end(); at++) {
            char ch = source.charAt(at);
            if (ch == '\n' || ch == '\r')
                sql.append(ch);
        }
        if (sql.length() == start)
            sql.append(' ');
    }
}
