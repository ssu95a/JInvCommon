package ru.inversion.query.node;

import ru.inversion.parser.token.Token;

import java.util.*;

import static ru.inversion.parser.token.Token.TypeEnum.UNCHANGED_MARK;


/**
 *
 * @author ssu
 */
public class NodeHandler implements Iterable<INode> {

    /** */
    final private Iterator<Token> tokenIterator;

    /** */
    public static class Context {

        private Token
                    current,
                    next;
        /** */
        private CompositeNode parent;

        private boolean replaceConstant = true;

        /** */
        public Context( ) {
        }

        public void switchReplaceConstant() {
            replaceConstant = !replaceConstant;
        }

        public boolean isReplaceConstant() {
            return replaceConstant;
        }

        /** */
        public Context move( Token n ) {

            this.current = next;
            this.next    = n;

            return this;
        }

        /** */
        public Token current() {
            return current;
        }

        /** */
        public Token next() {
            return next;
        }

        /** */
        public CompositeNode getParent() {
            return parent;
        }

        /** */
        public void setParent( CompositeNode parent ) {
            this.parent = parent;
        }
    }

    /** */
    public NodeHandler( Iterator<Token> tokenIterator ) {
        this.tokenIterator = tokenIterator;
    }

    /** */
    @Override
    public Iterator<INode> iterator( ) {

        if( !tokenIterator.hasNext( ) )
             return Collections.emptyIterator( );

        final Context context = new Context( );
        context.move( tokenIterator.next() );

        return new Iterator< INode >() {

            final Queue<INode> nodeQueue = new LinkedList<>();
            {
                getNextNode( );
            }

            @Override
            public boolean hasNext() {
                return !nodeQueue.isEmpty() ;
            }

            /** */
            private Token getNextToken() {
                if( tokenIterator.hasNext() )
                    return tokenIterator.next();
                return
                    null;
            }

            @Override
            public INode next() {

                if( !hasNext() )
                    throw new NoSuchElementException();

                INode n = nodeQueue.poll();
                getNextNode();
                return n;
            }

            private void getNextNode( ) {

                Token t = null;

                while( ( t = getNextToken() ) != null )
                {
                    if( NodeHandler.this.handle( context.move( t ), nodeQueue ) )
                        break;
                }
            }
        };
    }

    /** */
    private List<Token> tokenList;

    /** */
    private boolean handle( Context context, Queue<INode> nodeQueue ) {

        if( context.current().getType() == UNCHANGED_MARK )
        {
            context.switchReplaceConstant();
            return false;
        }

        final Token.TypeEnum tokenType = context.current().getType();

        INode node = null;

        switch ( tokenType )
        {
            case PARENTHESIS_LEFT:
            {
                context.setParent( new CompositeNode( context.getParent() ) );
                node = context.getParent();
            }
            break;

            case PARENTHESIS_RIGHT:
            {
                CompositeNode parent = context.getParent();
                if( parent != null )
                    context.setParent( parent.getParent() );
                //node = context.next() == null ?  Node.EMPTY : null;
                return false;
            }
            //break;

            case SELECT:
            {
                if( context.getParent() == null )
                    node = new QueryNode( NodeTypeEnum.SELECT );
            }
            break;

            case ORDER_BY:
            {
                if( context.getParent() == null )
                    node = new OrderByNode( );
            }
            break;

            case FROM:
            {
                if( context.getParent() == null )
                    node = new QueryNode( NodeTypeEnum.FROM );
            }
            break;

            case WHERE :
            case HAVING:
            {
                if( context.getParent() == null )
                    node = new PredicateNode();
            }
            break;

            case INTEGER:
            case STRING :
            case DECIMAL:
            {
                if( context.isReplaceConstant() )
                    node = new ParameterNode( context.getParent(), context.current() );
            }
            break;

            case INDEXED_PARAMETER:
            case NAMED_PARAMETER  :
                node = new ParameterNode( context.getParent(), context.current() );
            break;

            case OPTIMIZER_HINT:
                node = new OptimizerHintNode( context.current() );
            break;
        }

        if( node == null )
        {
            if( tokenList == null )
                tokenList = new ArrayList<>();

            tokenList.add( context.current );

            return false;
        }

        if( tokenList != null )
        {
            nodeQueue.add( new ExpressionNode( context.getParent(), tokenList ) );
            tokenList = null;
        }

        nodeQueue.add( node );

        return true;
    }
}
