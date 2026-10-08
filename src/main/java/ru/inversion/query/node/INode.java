package ru.inversion.query.node;

import java.util.Collections;
import java.util.List;

/**
 * Created by ssu on 13.07.2017.
 */
public interface INode  {

    /** */
    NodeTypeEnum getType( );

    /** */
    CompositeNode getParent( );

    /** получить уровень вложенности */
    default int getLevel( ) {
        return getParent() == null ? 0 : getParent().getLevel() + 1;
    }

    /** */
    default List<INode> getChildren() { return Collections.emptyList();}

}
