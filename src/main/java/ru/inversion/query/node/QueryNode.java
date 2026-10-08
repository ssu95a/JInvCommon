package ru.inversion.query.node;

/** */
public class QueryNode implements INode {

    final private NodeTypeEnum nodeType;

    public QueryNode( NodeTypeEnum nodeType ) {
        this.nodeType = nodeType;
    }
    @Override
    public NodeTypeEnum getType() {
        return nodeType;
    }
    @Override
    public CompositeNode getParent() {
        return null;
    }
    @Override
    public String toString() { return getType().toString(); }
}
