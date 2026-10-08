package ru.inversion.query.node;

/** */
public class PredicateNode extends Node {
    public PredicateNode() {
        super();
    }
    @Override
    public NodeTypeEnum getType( ) {
        return NodeTypeEnum.PREDICATE;
    }
    @Override
    public String toString() { return "PredicateNode"; }
}
