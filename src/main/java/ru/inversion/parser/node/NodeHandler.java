package ru.inversion.parser.node;

import ru.inversion.parser.token.Token;

import ru.inversion.parser.node.INodeState.Context;

import java.util.*;

import static ru.inversion.parser.node.INode.IGNORE_NODE;

/**
 *
 * @author ssu
 */
public class NodeHandler implements Iterable<INode> {

    /** */
    private INodeState<INode> currentState;

    final private Iterator<Token> tokenIterator;

    /** */
    public NodeHandler( Iterator<Token> tokenIterator ) {
        this.tokenIterator = tokenIterator;
    }

    /** */
    @Override
    public Iterator< INode > iterator( ) {

        if( !tokenIterator.hasNext( ) )
             return Collections.emptyIterator( );

        Context context = new Context( );

        context.move( tokenIterator.next() );

        return new Iterator<INode>() {

            @Override
            public boolean hasNext() {
                return context.next() != null ;
            }

            @Override
            public INode next() {

                INode node = null;

                do {
                    node = NodeHandler.this.handle( context.move( getNextToken() ));
                } while( node == null );

                return node;
            }

            /** */
            private Token getNextToken() {
                if( tokenIterator.hasNext() )
                    return tokenIterator.next();
                return
                    null;
            }
        };
    }

    /** */
    private INode handle( Context context ) {

        if( currentState != null ) {

            INode node = currentState.apply(context);

            if( node != null ) {

                currentState = null;

                return node;
            }

            return null;
        }

        if( context.current().getType() == Token.TypeEnum.PARENTHESIS_RIGHT ) {

            CompositeNode parent = context.getParent();

            if( parent != null )
                context.setParent( parent.getParent() );

            return IGNORE_NODE;
        }

        if( context.current().getType() == Token.TypeEnum.PARENTHESIS_LEFT) {

            context.setParent( new CompositeNode( context.getParent() ) );

            return context.getParent();
        }

        currentState = DotNameState.isMatch( context );

        if( currentState != null ) {

            currentState.apply(context);

            return null;
        }

        return new Node( context.getParent(), context.current() );
    }
}
