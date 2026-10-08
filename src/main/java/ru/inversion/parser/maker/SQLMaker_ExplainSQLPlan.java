package ru.inversion.parser.maker;

import ru.inversion.parser.statement.PrepareContext;
import ru.inversion.parser.token.Token;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

public class SQLMaker_ExplainSQLPlan extends AbstractSQLMakerBase  {

    private int nIndex = 1;

    @Override
    public void accept( Token t, PrepareContext c ) {

        switch( t.getType() ) {
            case UNCHANGED_MARK:
                c.setReplaceConstantMode();
                break;
            case INDEXED_PARAMETER:
            case NAMED_PARAMETER:
                c.append(" :p").append( nIndex++ );
                break;
            case INTEGER:
            case DECIMAL:
            case STRING:
                if( c.isReplaceConstantMode() ) {
                    c.append(" :p").append( nIndex++ );
                }
                else
                    c.sp().append( value2Str( t.getValue() ) ).sp();
                break;
            default :
                c.sp().append( t.getValue() );
        }

    }
}
