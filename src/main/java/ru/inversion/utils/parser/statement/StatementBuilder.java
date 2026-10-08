package ru.inversion.utils.parser.statement;

import ru.inversion.utils.parser.Token;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static ru.inversion.utils.parser.Token.ParenthesisLeft;
import static ru.inversion.utils.parser.Token.ParenthesisRight;

public final class StatementBuilder {

    private final Iterator<Token<?>>   tokens;
    private final List<IStatementPart> parts = new ArrayList<>();

    private final StringBuilder    sb      = new StringBuilder();
    private final StatementContext context = new StatementContext();

    private int level = 0;

    public StatementBuilder( Iterator<Token<?>> tokens )
    {
        this.tokens = tokens;
    }

    public SqlStatement build() {

        while( tokens.hasNext() )
        {
            accept( tokens.next() );
        }

        //flushText();

        return new SqlStatement(parts);
    }

    public void accept(Token<?> t) {
/*
        if( t == ParenthesisLeft )
            level++;
        else if( t == ParenthesisRight )
            level --;

        switch (t.getType()) {

            case NAMED_PARAMETER:
                flushText();
                parameters.add(
                        new ParameterPart(++jdbcIndex, t.getValue())
                );
                parts.add(parameters.get(parameters.size() - 1));
                break;

            case INDEXED_PARAMETER:
                flushText();
                parameters.add(
                        new ParameterPart(++jdbcIndex, null)
                );
                parts.add(parameters.get(parameters.size() - 1));
                break;

            case PG_POSITIONAL_PARAMETER:
                flushText();
                parameters.add(
                        new ParameterPart(++jdbcIndex, (Integer) t.getValue())
                );
                parts.add(parameters.get(parameters.size() - 1));
                break;

            case ORDER_BY:
                if (parenLevel == 0) {
                    flushText();
                    inOrderBy = true;
                    orderByBuilder = new StatementBuilder();
                } else {
                    textBuffer.write("ORDER BY ");
                }
                break;
            default:
                if (inOrderBy) {
                    orderByBuilder.accept(t);
                } else {
                    textBuffer.write(t.getValue().toString());
                    textBuffer.write(' ');
                }
        }

 */
    }
}
