package ru.inversion.dataset.impl;

import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.utils.S;
import ru.inversion.utils.Triplet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author ssu
 */
public abstract class SelectBuilder {
    
    protected String
                    hint, 
                    orderBy, 
                    mark,
                    queryAlias,
                    aggrTable;
    
    protected boolean 
                    additionalTags = false;

    protected List<String> 
                    predicateList;
    
    protected List< Triplet<String,AggrFuncEnum,String> >
                    aggrList;

    /** */
    public SelectBuilder additionalTags( boolean v ) {
        this.additionalTags = v;
        return this;
    }
    
    /** */
    public SelectBuilder hint( String hint ) {
        this.hint = hint;
        return this;
    }
    
    /** */
    public String getAggrTable( ) {
        return aggrTable;
    }
    
    /** */
    public SelectBuilder aggrTable( String aggrTable ) {
        this.aggrTable = aggrTable;
        return this;
    }
    
    /** */
    public String getQueryAlias() {
        return queryAlias;
    }
    
    /** */
    public SelectBuilder queryAlias( String expressionAlias ) {
        this.queryAlias = expressionAlias;
        return this;
    }
    
    /** */
    public SelectBuilder orderBy( String orderBy ) {
        this.orderBy = orderBy;
        return this;
    }
    
    /** */
    public SelectBuilder mark( String mark ) {
        this.mark = mark;
        return this;
    }
    
    /** */
    public SelectBuilder addPredicat( String predicat ) {

        if( S.isNotNullOrEmpty( predicat ) ) {
            
            if( predicateList == null )
                predicateList = new ArrayList<>();
            
            predicateList.add( predicat );
        }
        
        return this;
    }
    
    /** */
    public SelectBuilder addPredicat( Collection<String> pl ) {
        
        if( pl != null && !pl.isEmpty() ) 
        {
            if( predicateList == null )
                predicateList = new ArrayList<>();
            
            predicateList.addAll(pl);
        }
        
        return this;
    }
    
    /** */
    public SelectBuilder addAggregator( String columnExpr, AggrFuncEnum func, String alias ) {
        
        if( aggrList == null )
            aggrList = new ArrayList<>();
        
        aggrList.add( Triplet.makeTriplet( columnExpr, func, alias ) );
        
        return this;
    }
    
    /** */
    public void clear( ) {
        hint          = null; 
        orderBy       = null; 
        mark          = null;
        predicateList = null;
        aggrList      = null;
        additionalTags=false;
        aggrTable     = null;
    }

    /** */
    protected String prepareAggrExpression( String tableFor ) {
        
        StringBuilder sb = new StringBuilder( );

        sb.append( "SELECT\n" );

//        if( additionalTags )
//            sb.append("--A[\n");

        int i = 0;

        for( Triplet<String,AggrFuncEnum,String> t: aggrList ) {

            i++;

            if( i > 1 )
               sb.append(",");

            if( t.second != AggrFuncEnum.NONE )
                sb.append( t.second );

            sb.append( " (" )
              .append( t.first )
              .append( ") " );

            if( S.isNullOrEmpty( t.third ) )
                sb.append( '"'  )
                  .append( "A$" )
                  .append( t.first )
                  .append( '"'  );
            else
                sb.append( t.third );
        }//end for

        sb.append("\n");

//        if( additionalTags )
//            sb.append("--A]\n");

        sb.append(" from ").append( tableFor ).append(" \n");
        
		if( predicateList != null ) {
			
//            if( additionalTags )
//                sb.append("--W[\n");
            
            if( predicateList != null ) {

                sb.append( "WHERE null is null\n" );
                
                predicateList.stream().forEach( (s) -> sb.append(" and (").append( s ).append(")\n") );
            }
            
//            if( additionalTags )
//        		sb.append("--W]\n");
		}
            
//        sb.append(") arslt \n");
            
//            if( additionalTags )
//                sb.append("--S]\n");

/*        
        if( orderBy != null ) {
            
            if( additionalTags )
                sb.append("--O[\n");
            
            sb.append(" ORDER BY ").append( orderBy ).append("\n");

            if( additionalTags )
                sb.append("--O]\n"); 
        }
*/        
        return sb.toString( );
    }
    
    
    abstract public String getFromObj( );
    
    abstract public String build( );
    
}
