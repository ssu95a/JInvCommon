package ru.inversion.datacall.maker;

import ru.inversion.datacall.AbstractCallMakerBase;
import ru.inversion.datacall.CallPrepareContext;
import ru.inversion.parser.token.Token;

public class CallMaker_WithParametersValue extends AbstractCallMakerBase {

    @Override
    public void accept( Token t, CallPrepareContext c ) {
        switch( t.getType() ) {
            case INDEXED_PARAMETER:
                c.append(t);
                break;
            case NAMED_PARAMETER:
                c.append(t);
                break;
            default:
                c.append( t );
        }
    }
}
