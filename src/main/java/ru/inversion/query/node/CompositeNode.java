package ru.inversion.query.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by ssu on 13.07.2017.
 */
public class CompositeNode implements INode {

    /** */
    private List<INode> children;

    /** */
    final private CompositeNode parent;

    /** */
    public CompositeNode( CompositeNode parent ) {

        this.parent = parent;

        if( parent != null )
            parent.addChild(this);
    }

    /** */
    public void addChild( INode child ) {

        if( children == null )
            children = new ArrayList<>( );

        children.add( child );
    }

    /** */
    public List<INode> getChildren( ) {
        return children == null ? Collections.< INode >emptyList() : children;
    }

    @Override
    public NodeTypeEnum getType() {
        return NodeTypeEnum.COMPOSITE;
    }

    @Override
    public CompositeNode getParent() {
        return parent;
    }

    public String toString() {
        return "type=COMPOSITE, -->" ;
    }
}
