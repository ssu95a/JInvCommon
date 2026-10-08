package ru.inversion.query;

import ru.inversion.query.node.INode;
import ru.inversion.query.node.NodeTypeEnum;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;


/**
 *
 */
public class QueryDescriptor
{
    /** */
    private List<INode> nodeList;

    private Map< NodeTypeEnum, Integer> positions = new EnumMap<>(NodeTypeEnum.class);

    /** */
    private QueryDescriptor( ) { }

    /*
    public boolean isAggregate( ) {
        return isAggregate;
    }
    */

    public void prepareSql() { }

}
