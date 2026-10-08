package ru.inversion.datacall.maker;

import ru.inversion.datacall.AbstractCallMakerBase;
import ru.inversion.datacall.CallPrepareContext;
import ru.inversion.parser.token.Token;

public class CallMaker_WithParametersValue extends AbstractCallMakerBase {

    @Override
    public void accept( Token t, CallPrepareContext c ) {
        switch( t.getType() ) {
            case INDEXED_PARAMETER:
                //c.append( value2Str( c.callBackParameters().getParameter(nIndex++) ) );
                break;
            case NAMED_PARAMETER:
                //c.append( value2Str( c.callBackParameters().getParameter( t.getValue().toString() ) ) );
                break;
            default:
                c.append( t );
        }
    }
}
