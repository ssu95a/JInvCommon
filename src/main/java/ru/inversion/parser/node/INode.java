package ru.inversion.parser.node;

import ru.inversion.parser.statement.PrepareContext;

import java.util.Collections;
import java.util.List;

/**
 * Created by ssu on 13.07.2017.
 */
public interface INode {

    final public static INode IGNORE_NODE = new INode() {
        @Override
        public NodeTypeEnum getType() {
            return null;
        }
        @Override
        public Object getValue() {
            return null;
        }
        @Override
        public CompositeNode getParent() {
            return null;
        }
    };

    /** */
    NodeTypeEnum getType( );

    /** */
    Object getValue();

    /** */
    CompositeNode getParent( );

    /** получить уровень вложенности */
    default int getLevel( ) {
        return getParent() == null ? 0 : getParent().getLevel() + 1;
    }

    /** */
    default List<INode> getChildren() { return Collections.emptyList();}
}
