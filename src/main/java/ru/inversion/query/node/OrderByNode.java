package ru.inversion.query.node;

public class OrderByNode implements INode {

    private String override;

    public OrderByNode() {

    }
    @Override
    public NodeTypeEnum getType() {
        return NodeTypeEnum.ORDER_BY;
    }
    @Override
    public CompositeNode getParent() {
        return null;
    }
    @Override
    public String toString() {
        return "OrderByNode";
    }
}
