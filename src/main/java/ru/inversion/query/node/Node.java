package ru.inversion.query.node;

/**
 *
 * @author ssu
 */
public abstract class Node implements INode {

    static final public Node EMPTY = new Node() {
        @Override
        public NodeTypeEnum getType() {
            return null;
        }
    };

    /** */
    final private CompositeNode parent;

    /** */
    public Node( ) {
        this( null );
    }

    /** */
    public Node( CompositeNode parent ) {

        this.parent = parent;

        if( parent != null )
            parent.addChild(this);
    }

    /** */
    public abstract NodeTypeEnum getType();

    /** */
    @Override
    public CompositeNode getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return "Node: type " + getType();
    }
}
