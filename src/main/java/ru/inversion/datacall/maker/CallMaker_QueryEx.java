package ru.inversion.datacall.maker;

import ru.inversion.datacall.AbstractCallMakerBase;
import ru.inversion.datacall.CallPrepareContext;
import ru.inversion.parser.token.Token;

import java.util.List;
import java.util.function.Function;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

public class CallMaker_QueryEx extends AbstractCallMakerBase {

    /** */
    @Override
    public void accept( Token t, CallPrepareContext c ) {

        List ps = null;//c.parametersValues();
        List< Function > parameters = (List<Function>)ps;

        switch( t.getType() ) {
            case INDEXED_PARAMETER:
                nIndex++;
                c.append(" ?");
                //parameters.add( (Object o) -> c.callBackParameters().getParameter(nIndex) );
            break;
            case NAMED_PARAMETER:
                c.append(" ?");
                String name = (String)t.getValue();
                //parameters.add( (Object o) -> c.callBackParameters().getParameter(name) );
            break;
            case INTEGER:
            case DECIMAL:
            case STRING:
                if( c.isReplaceConstantMode() ) {
                    c.append(" ?");
                    parameters.add( (o)->t.getValue() );
                }
                else
                    c.sp().append( value2Str( t.getValue() ) ).sp();
            break;
            default :
                c.append( t );
        }
    }
}
