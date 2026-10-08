package ru.inversion.parser.node;

import ru.inversion.parser.token.Token;

import static ru.inversion.parser.token.Token.TypeEnum.*;

public class DotNameState implements INodeState<INode> {

    private Token first;

    private boolean wasDot = false;

    /** */
    public DotNameState( ) {
    }

    /** */
    @Override
    public Node apply( Context context ) {

        if( wasDot ) {
            String s = first.getValue() + "." + context.current().getValue();
            return new Node( context.getParent(), new Token( EXPRESSION, s) );
        }

        if( context.current().getType() == DOT ) {
            wasDot = true;
            return null;
        }

        first = context.current();

        return null;
    }

    /** */
    public static INodeState<INode> isMatch( INodeState.Context context ) {

        Token t1 = context.current();
        Token t2 = context.next();

        if( t2 != null && t1.getType() == EXPRESSION && t2.getType() == DOT ) {
            return new DotNameState();
        }

        return null;
    }
}
