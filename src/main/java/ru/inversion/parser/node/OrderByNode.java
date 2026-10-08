package ru.inversion.parser.node;

/** */
public class OrderByNode implements INode {

    private String overrideValue;

    /** */
    @Override
    public NodeTypeEnum getType() {
        return NodeTypeEnum.ORDER_BY;
    }

    /** */
    @Override
    public Object getValue() {
        return overrideValue;
    }

    /** */
    public void setValue( String overrideValue ) {
        this.overrideValue = overrideValue;
    }

    /** */
    @Override
    public CompositeNode getParent( ) {
        return null;
    }

    /** */
    public boolean isOverride() { return overrideValue != null; }
}
