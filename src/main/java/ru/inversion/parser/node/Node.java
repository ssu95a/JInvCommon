package ru.inversion.parser.node;

import ru.inversion.parser.token.Token;
import static ru.inversion.parser.token.Token.TypeEnum.GROUP_BY;
import static ru.inversion.parser.token.Token.TypeEnum.ORDER_BY;
import ru.inversion.utils.U;

/**
 *
 * @author ssu
 */
public class Node implements INode {

    /** */
    private NodeTypeEnum type;

    /** */
    final private CompositeNode parent;

    /** */
    final private Token token;

    /** */
    public Node( Token token ) {
        this( null, token );
    }

    /** */
    public Node( CompositeNode parent, Token token ) {

        this.parent = parent;
        this.token  = token;

        if( token.getType() == Token.TypeEnum.EXPRESSION ) {
            this.type = INodeState.g_getNodeType.apply( ((String)token.getValue()).toUpperCase() );
        } else {
            this.type = U.decode(token.getType(), ORDER_BY, NodeTypeEnum.ORDER_BY, GROUP_BY, NodeTypeEnum.GROUP_BY);
        }

        if( parent != null )
            parent.addChild(this);
    }

    /** */
    public Node( CompositeNode parent, NodeTypeEnum type ) {

        this.parent = parent;
        this.token  = null;
        this.type   = type;

        if( parent != null )
            parent.addChild(this);
    }

    /** */
    public NodeTypeEnum getType() {
        return type;
    }

    /** */
    public Token getToken( ) { return token; }

    /** */
    public Object getValue( ){
        return getToken( ).getValue( );
    }

    /** */
    @Override
    public CompositeNode getParent() {
        return parent;
    }

    /** */
    @Override
    public String toString() {
        return "type=" + type + ", val= " + getValue() + ", token = " + (getToken() == null ? null : getToken().getType().name());
    }
}
