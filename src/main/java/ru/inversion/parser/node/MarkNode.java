package ru.inversion.parser.node;

import ru.inversion.query.QueryDescriptor;

/** */
public class MarkNode implements INode {

    final QueryDescriptor dtor;

    /** */
    public MarkNode( QueryDescriptor dtor ) {
        this.dtor = dtor;
    }

    /** */
    @Override
    public NodeTypeEnum getType() {
        return NodeTypeEnum.MARK;
    }

    /** */
    @Override
    public Object getValue( ) {
        return dtor;
    }

    /** */
    @Override
    public CompositeNode getParent( ) {
        return null;
    }
}
