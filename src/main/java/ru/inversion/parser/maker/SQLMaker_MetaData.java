package ru.inversion.parser.maker;

import ru.inversion.parser.statement.PrepareContext;
import ru.inversion.parser.token.Token;

import static ru.inversion.dataset.parser.SqlValueFormatter.format;

public class SQLMaker_MetaData extends AbstractSQLMakerBase  {

    @Override
    public void accept( Token t, PrepareContext c ) {

        switch( t.getType() ) {
            case UNCHANGED_MARK:
                c.setReplaceConstantMode();
                break;
            case INDEXED_PARAMETER:
            case NAMED_PARAMETER:
                c.append(" null ");
                break;
            case INTEGER:
            case DECIMAL:
            case STRING:
                if( c.isReplaceConstantMode() ) {
                    c.append(" null ");
                }
                else
                    c.sp().append( format( t.getValue() ) ).sp();
                break;
            default :
                c.sp().append( t.getValue() );
        }

    }
}
