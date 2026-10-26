package ru.inversion.datacall.maker;

import ru.inversion.datacall.AbstractCallMakerBase;
import ru.inversion.datacall.CallPrepareContext;
import ru.inversion.parser.token.Token;
import ru.inversion.utils.TriConsumer;
import ru.inversion.utils.sparser.SqlValueFormatter;

public class CallMaker_Query extends AbstractCallMakerBase {

    private int nCounter = 0;

    /** */
    @Override
    public void accept( Token t, CallPrepareContext c ) {

        TriConsumer< Integer,Comparable,Object > handler = c.parameterHandler();

        if( t == null || t.getType() == null )
            throw new IllegalArgumentException("Token 't' is null");

        switch( t.getType() ) {
            case INDEXED_PARAMETER:
                handler.accept( ++nCounter, nIndex++, null );
                //parameters.add( c.callBackParameters().getParameter(nIndex++) );
                c.sp().append("? ");
                if( c.isCommentParameters() )
                    c.append("/* ").append(nCounter).append(", :").append( t.getValue() ).append(" */ ");
            break;
            case NAMED_PARAMETER:
                //parameters.add( c.callBackParameters().getParameter( t.getValue().toString() ) );
                handler.accept( ++nCounter, (Comparable) t.getValue(), null );
                c.sp().append("? ");
                if( c.isCommentParameters() )
                    c.append("/* ").append(nCounter).append(", :").append( t.getValue() ).append(" */ ");
            break;
            case INTEGER:
            case DECIMAL:
            case STRING:
                if( c.isReplaceConstantMode() ) {

                    handler.accept( ++nCounter, t.getType(), t.getValue() );

                    c.sp().append("? ");
                    if( c.isCommentParameters() )
                        c.append("/* ").append(nCounter).append(", :").append( t.getValue() ).append(" */ ");
                }
                else
                    c.append( SqlValueFormatter.format( t.getValue() ) ).sp();
            break;
            default :
                c.append( t );
        }
    }
}
