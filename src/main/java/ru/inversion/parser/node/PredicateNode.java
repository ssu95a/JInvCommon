package ru.inversion.parser.node;

import ru.inversion.query.QueryDescriptor;

import java.util.ArrayList;
import java.util.List;

/** */
public class PredicateNode implements INode {

    final List< QueryDescriptor > predicateList = new ArrayList<>();

    final private NodeTypeEnum nodeType;
    final private boolean      created;

    /** */
    public PredicateNode( NodeTypeEnum nodeType, boolean created ) {
        this.nodeType = nodeType;
        this.created  = created;
    }

    /** */
    public void appendPredicate( QueryDescriptor dtor ) {
        predicateList.add(dtor);
    }

    /** */
    @Override
    public NodeTypeEnum getType( ) {
        return nodeType;
    }

    /** */
    @Override
    public Object getValue() {
        return null;
    }

    /** */
    @Override
    public CompositeNode getParent() {
        return null;
    }

    public List< QueryDescriptor > getPredicateList() {
        return predicateList;
    }

    public boolean isCreated() {
        return created;
    }
}
