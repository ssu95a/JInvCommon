package ru.inversion.dataset.impl;

import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.utils.S;
import ru.inversion.utils.Triplet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Compatibility base facade for the former SelectBuilder hierarchy.
 *
 * SQLDataSet itself does not depend on this class.
 */
public abstract class SelectBuilder {

    protected String hint;
    protected String orderBy;
    protected String mark;
    protected String queryAlias;
    protected String aggrTable;

    protected boolean additionalTags = false;

    protected List<String> predicateList;
    protected List<Triplet<String,AggrFuncEnum,String>> aggrList;

    public SelectBuilder additionalTags( boolean value ) {
        this.additionalTags = value;
        return this;
    }

    public SelectBuilder hint( String value ) {
        this.hint = value;
        return this;
    }

    public String getAggrTable() {
        return aggrTable;
    }

    public SelectBuilder aggrTable( String value ) {
        this.aggrTable = value;
        return this;
    }

    public String getQueryAlias() {
        return queryAlias;
    }

    public SelectBuilder queryAlias( String value ) {
        this.queryAlias = value;
        return this;
    }

    public SelectBuilder orderBy( String value ) {
        this.orderBy = value;
        return this;
    }

    public SelectBuilder mark( String value ) {
        this.mark = value;
        return this;
    }

    public SelectBuilder addPredicat( String predicate ) {
        if( S.isNotNullOrEmpty(predicate) ) {
            if( predicateList == null )
                predicateList = new ArrayList<>();
            predicateList.add(predicate);
        }
        return this;
    }

    public SelectBuilder addPredicat( Collection<String> predicates ) {
        if( predicates != null && !predicates.isEmpty() ) {
            if( predicateList == null )
                predicateList = new ArrayList<>();
            predicateList.addAll(predicates);
        }
        return this;
    }

    public SelectBuilder addAggregator(
            String columnExpr,
            AggrFuncEnum func,
            String alias
    ) {
        if( aggrList == null )
            aggrList = new ArrayList<>();

        aggrList.add(Triplet.makeTriplet(columnExpr, func, alias));
        return this;
    }

    /**
     * Keeps queryAlias, matching the former SelectBuilder contract.
     */
    public void clear() {
        hint = null;
        orderBy = null;
        mark = null;
        predicateList = null;
        aggrList = null;
        additionalTags = false;
        aggrTable = null;
    }

    /**
     * Legacy aggregate surface kept local to compatibility facades.
     */
    protected String prepareAggrExpression( String tableFor ) {

        StringBuilder sql = new StringBuilder("SELECT\n");
        int index = 0;

        for( Triplet<String,AggrFuncEnum,String> aggregate : aggrList ) {
            if( index++ > 0 )
                sql.append(',');

            if( aggregate.second != AggrFuncEnum.NONE )
                sql.append(aggregate.second);

            sql.append(" (")
               .append(aggregate.first)
               .append(") ");

            if( S.isNullOrEmpty(aggregate.third) )
                sql.append('"').append("A$").append(aggregate.first).append('"');
            else
                sql.append(aggregate.third);
        }

        sql.append("\n from ").append(tableFor).append(" \n");

        if( predicateList != null && !predicateList.isEmpty() ) {
            sql.append("WHERE null is null\n");
            for( String predicate : predicateList )
                sql.append(" and (").append(predicate).append(")\n");
        }

        return sql.toString();
    }

    public abstract String getFromObj();

    public abstract String build();
}
