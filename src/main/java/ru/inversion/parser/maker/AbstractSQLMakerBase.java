package ru.inversion.parser.maker;


import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.parser.node.*;
import ru.inversion.parser.statement.PrepareContext;
import ru.inversion.parser.statement.PrepareModeEnum;
import ru.inversion.parser.token.Token;
import ru.inversion.query.QueryDescriptor;
import ru.inversion.utils.Tags;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static ru.inversion.parser.token.Token.TypeEnum.UNTOUCHABLE_INTEGER;

/** */
public abstract class AbstractSQLMakerBase implements BiConsumer<INode, PrepareContext > {

    public static BiConsumer<INode, PrepareContext > createSQLMaker( PrepareModeEnum prepareMode ) {

        switch( prepareMode ) {
            case QUERY:
                return new SQLMaker_Query();
            case QUERY_EX:
                return new SQLMaker_QueryEx();
            case EXPLAIN_PLAN:
                return new SQLMaker_ExplainSQLPlan();
            case META_DATA:
                return new SQLMaker_MetaData();
            case QUERY_WITH_VALUES:
                return new SQLMaker_WithParametersValue();
        }

        throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "Error 'prepareMode' value: " + prepareMode );
    }

    @Override
    public void accept( INode n, PrepareContext c ) {

        if( n == null ) {
            return;
        }

        if( n instanceof CompositeNode ) {
            accept( (CompositeNode)n, c );
        }
        else if( n instanceof PredicateNode) {
            accept( (PredicateNode)n, c );
        }
        else if( n instanceof MarkNode) {
            accept( (MarkNode)n, c );
        }
        else if( n instanceof OrderByNode) {
            accept( (OrderByNode)n, c );
        }
        else
            accept( (Node) n, c );
    }

    /** */
    public abstract void accept( Token t, PrepareContext c );

    /** */
    public void accept( Node n, PrepareContext c ) {

        if( n.getType() == null )
        {
            accept( n.getToken(), c );
        }
        else
        {
            switch( n.getType() ) {
                case GROUP_BY:
                    c.append(" GROUP BY ");
                    break;
                case ORDER_BY:
                    c.append(" ORDER BY ");
                    break;
                case FROM:
                    c.append(" FROM ");
                    break;
                case WHERE:
                    c.append(" WHERE");
                    break;
                default: {
                    c.sp().append( n.getType().name() );
                }
            }
        }
    }

    /** */
    public void accept( OrderByNode on, PrepareContext c ) {
        c.append(" ORDER BY ");
        QueryDescriptor dtor = (QueryDescriptor)on.getValue();
        if ( dtor != null ){
            ;//dtor.prepareSql( c );
        }
    }

    /** */
    public void accept( CompositeNode cn, PrepareContext c ) {

        c.incrementLevel();

        c.append("( ");

        final AtomicBoolean containsOrderBy = new AtomicBoolean( false );
        final AtomicInteger orderByIndex = new AtomicInteger( -1 );

        cn.getChildren().forEach( (INode n) -> {
            if ( n.getType() == NodeTypeEnum.ORDER_BY ){
                containsOrderBy.set( true );
                orderByIndex.set( cn.getChildren().indexOf( n ) );
            }
        } );

        cn.getChildren().forEach( (INode n)-> {
            // Ищем цифры после ORDER BY и не даём их подменять
            if ( containsOrderBy.get()
                    && orderByIndex.get() < cn.getChildren().indexOf( n )
                    && n instanceof Node
                    && ( (Node) n ).getToken().getType() == Token.TypeEnum.INTEGER )
            {
                accept( new Node( new Token( UNTOUCHABLE_INTEGER, ( (Node) n ).getToken().getValue() ) ), c );
            } else {
                accept( n, c );
            }
        } );

        c.append(" )");

        c.decrementLevel();
    }

    /** */
    public void accept( MarkNode cn, PrepareContext c ) {

        c.append("( ");

        QueryDescriptor dtor = (QueryDescriptor)cn.getValue();

        //dtor.prepareSql( c );

        c.append(") ").append( IMarkable.MARK_COLUMN );
    }

    /** */
    public void accept( PredicateNode pn, PrepareContext c ) {

        c.sp().append( pn.getType().toString() ).sp();

        boolean first = true;

        for( QueryDescriptor d : pn.getPredicateList() ) {

            if( first ) {
                first = false;
            } else {
                c.append(" AND ");
            }

            //d.prepareSql( c );
        }

        if( !pn.isCreated() && !pn.getPredicateList().isEmpty() ){
            c.append(" AND ");
        }
    }
}
