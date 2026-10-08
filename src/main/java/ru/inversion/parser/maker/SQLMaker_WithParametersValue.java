package ru.inversion.parser.maker;

import ru.inversion.parser.statement.PrepareContext;
import ru.inversion.parser.token.Token;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

/** */
public class SQLMaker_WithParametersValue extends AbstractSQLMakerBase {

    private int nIndex = 0;

    @Override
    public void accept( Token t, PrepareContext c ) {

        switch( t.getType() ) {
            case UNCHANGED_MARK:
                break;
            case INDEXED_PARAMETER:
                c.append( value2Str( c.callBackParameters().getParameter(nIndex++) ) );
                break;
            case NAMED_PARAMETER:
                c.append( value2Str( c.callBackParameters().getParameter( t.getValue().toString() ) ) );
                break;
            case STRING:
            case INTEGER:
            case DECIMAL:
                c.sp().append( value2Str( t.getValue() ) ).sp();
            break;
            default:
                c.sp().append( t.getValue() );
        }
    }
}
