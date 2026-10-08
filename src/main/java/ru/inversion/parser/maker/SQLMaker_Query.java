package ru.inversion.parser.maker;

import ru.inversion.parser.statement.PrepareContext;
import ru.inversion.parser.token.Token;

import java.util.List;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

public class SQLMaker_Query extends AbstractSQLMakerBase {

    private int nIndex = 0;

    @Override
    public void accept( Token t, PrepareContext c ) {

        List<Object> parameters = c.parametersValues();

        switch( t.getType() ) {
            case UNCHANGED_MARK:
                c.setReplaceConstantMode();
            break;
            case INDEXED_PARAMETER:
                c.append(" ?");
                parameters.add( c.callBackParameters().getParameter(nIndex++) );
                break;
            case NAMED_PARAMETER:
                c.append(" ?");
                parameters.add( c.callBackParameters().getParameter( t.getValue().toString() ) );
                break;
            case INTEGER:
            case DECIMAL:
            case STRING:
                if( c.isReplaceConstantMode() ) {
                    c.append(" ?");
                    parameters.add( t.getValue() );
                }
                else
                    c.sp().append( value2Str( t.getValue() ) ).sp();
                break;
            case UNTOUCHABLE_INTEGER:
            default :
                c.sp().append( t.getValue() );
        }
    }
}
