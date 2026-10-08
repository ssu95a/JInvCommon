package ru.inversion.datacall.maker;

import ru.inversion.datacall.AbstractCallMakerBase;
import ru.inversion.datacall.CallPrepareContext;
import ru.inversion.parser.token.Token;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

public class CallMaker_QueryTextOnly extends AbstractCallMakerBase {

    private int nCounter = 1;

    /** */
    @Override
    public void accept( Token t, CallPrepareContext c ) {

        if( t == null || t.getType() == null )
            throw new IllegalArgumentException("Token 't' is null");

        switch( t.getType() ) {
            case INDEXED_PARAMETER:
                c.sp().append("? ");
                if( c.isCommentParameters() )
                    c.append("/* ").append(nCounter++).append(", :").append( t.getValue() ).append(" */ ");
            break;
            case NAMED_PARAMETER:
                c.sp().append("? ");
                if( c.isCommentParameters() )
                    c.append("/* ").append(nCounter++).append(", :").append( t.getValue() ).append(" */ ");
            break;
            case INTEGER:
            case DECIMAL:
            case STRING:
                if( c.isReplaceConstantMode() ) {
                    c.sp().append("? ");
                    if( c.isCommentParameters() )
                        c.append("/* ").append(nCounter++).append(", ").append( value2Str( t.getValue() ) ).append(" */ ");
                }
                else
                    c.append( value2Str( t.getValue() ) ).sp();
            break;
            default :
                c.append( t );
        }
    }
}
