package ru.inversion.datacall;

import ru.inversion.dataset.parser.lexer.SqlKeyword;
import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.parser.token.Token;
import ru.inversion.utils.converter.TypeConverter;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static ru.inversion.parser.token.Token.TypeEnum.*;

/** Представляет лексемы SqlScanner в формате существующих обработчиков вызова. */
final class CallTokenIterator implements Iterator<Token<?>> {
    private final String source;
    private final List<SqlScanner.Token> tokens;
    private int index;
    private int whitespaceOffset;
    private Token<?> next;

    CallTokenIterator(String source, List<SqlScanner.Token> tokens) {
        this.source = source;
        this.tokens = tokens;
    }

    @Override
    public boolean hasNext() {
        if (next == null)
            next = read();
        return next != null;
    }

    @Override
    public Token<?> next() {
        if (!hasNext())
            throw new NoSuchElementException();
        Token<?> token = next;
        next = null;
        return token;
    }

    private Token<?> read() {
        while (index < tokens.size()) {
            SqlScanner.Token token = tokens.get(index);
            if (token.kind() != SqlScanner.Kind.WHITESPACE) {
                index++;
                return convert(token);
            }

            whitespaceOffset = Math.max(whitespaceOffset, token.start());
            while (whitespaceOffset < token.end()) {
                if (source.charAt(whitespaceOffset++) == '\n')
                    return Token.Nl;
            }
            index++;
        }
        return null;
    }

    private Token<?> convert(SqlScanner.Token token) {
        switch (token.kind()) {
            case WORD:
                return word(token);
            case NUMBER:
                return number(token.text(source));
            case STRING:
                String text = token.text(source);
                if (text.charAt(0) == '\'')
                    return new Token<>(STRING, text.substring(1, text.length() - 1).replace("''", "'"));
                // Литералы с префиксом или dollar-кавычками сохраняются целиком.
                return new Token<>(EXPRESSION, text);
            case QUOTED_IDENTIFIER:
                return new Token<>(QUOTED_TEXT, token.text(source));
            case NAMED_PARAMETER:
                return new Token<>(NAMED_PARAMETER, source.substring(token.start() + 1, token.end()));
            case POSITIONAL_PARAMETER:
                return Token.IndexedParameter;
            case COMMENT:
            case PARAMETERIZE_OFF:
            case PARAMETERIZE_ON:
                return new Token<>(COMMENT, token.text(source));
            case HINT:
                return new Token<>(OPTIMIZER_HINT, token.text(source));
            case OPERATOR:
                return operator(token.text(source));
            case SYMBOL:
                return symbol(source.charAt(token.start()));
            default:
                return new Token<>(EXPRESSION, token.text(source));
        }
    }

    private Token<?> word(SqlScanner.Token token) {
        String text = token.text(source);
        SqlKeyword keyword = SqlKeyword.of(text);
        if (keyword != null) {
            switch (keyword) {
                case SELECT: return Token.Select;
                case FROM: return Token.From;
                case WHERE: return Token.Where;
                case HAVING: return Token.Having;
                case ORDER:
                case GROUP:
                    if (index + 1 < tokens.size()
                            && tokens.get(index).kind() == SqlScanner.Kind.WHITESPACE
                            && tokens.get(index + 1).isKeyword(source, SqlKeyword.BY)) {
                        index += 2;
                        return keyword == SqlKeyword.ORDER ? Token.OrderBy : Token.GroupBy;
                    }
                    break;
                default: break;
            }
        }
        return "END".equalsIgnoreCase(text) ? Token.End : new Token<>(EXPRESSION, text);
    }

    private static Token<?> number(String text) {
        if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0)
            return new Token<>(DECIMAL, new BigDecimal(text));
        return new Token<>(INTEGER, TypeConverter.convert(text, Number.class));
    }

    private static Token<?> operator(String text) {
        switch (text) {
            case ":=": return Token.Assignment;
            case "::": return Token.PgCastOperator;
            case "=>": return Token.NamedNotation;
            case "~": return Token.UnchangedMark;
            case "||": return new Token<>(CONCAT_STRINGS_OPERATOR, text);
            case "+": case "-": case "*": case "/":
                return new Token<>(ARITHMETIC_OPERATOR, text.charAt(0));
            case "=": case "<": case ">":
                return new Token<>(COMPARISON_OPERATOR, text.charAt(0));
            default:
                return new Token<>(COMPARISON_OPERATOR, text);
        }
    }

    private static Token<?> symbol(char ch) {
        switch (ch) {
            case '(': return Token.ParenthesisLeft;
            case ')': return Token.ParenthesisRight;
            case '{': return Token.LeftBrace;
            case '}': return Token.RightBrace;
            case ',': return Token.Comma;
            case '.': return Token.Dot;
            case ';': return Token.Semicolon;
            case '~': return Token.UnchangedMark;
            default: return new Token<>(EXPRESSION, String.valueOf(ch));
        }
    }
}
