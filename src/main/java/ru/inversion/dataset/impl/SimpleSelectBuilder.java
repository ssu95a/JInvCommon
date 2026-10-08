package ru.inversion.dataset.impl;

import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.dataset.parser.ExprInfo;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

/**
 *
 * @author ssu
 */
public class SimpleSelectBuilder extends SelectBuilder {

    final private String   select;
    final private ExprInfo exprInfo;
    
    /** */
    public SimpleSelectBuilder( String select, ExprInfo exprInfo ) {
        this.select     = select;
        this.exprInfo   = exprInfo;
        this.queryAlias = exprInfo.getAlias();
    }

    /** */
    private String buildSingle( ) {
        
        StringBuilder sb = new StringBuilder();

        int index = S.indexOfIgnoreCase( select, "select" );
        
        if( index == -1 )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "No select in query" );
        
        index += "select".length();
        
        String s1 = select.substring( 0, index  ),
               s2 = select.substring( index ); 

        if( additionalTags )
            sb.append("--S[\n");
        
        sb.append( s1 );

        if( hint != null )
            sb.append( " /*" ).append(hint).append("*/ ");

        StringBuilder sbMark = new StringBuilder();
        
        if( mark != null ) {
            
            sbMark.append( additionalTags ? "\n--M[\n" : "\n" )
                  .append(" (").append( mark ).append(") ").append( IMarkable.MARK_COLUMN )
                  .append( additionalTags ? ",\n--M]\n": ",\n");
            
            sb.append("::MARK::");
        }
        
        sb.append( s2 );

        if( additionalTags )
            sb.append("\n--S]\n");
        
		if( predicateList != null ) {
            
            StringBuilder sbWhere = new StringBuilder( );

            sbWhere.append( additionalTags ? "--W[\n" : "\n");
            
            if( !exprInfo.isHasWhere() )
                 sbWhere.append( "WHERE null is null\n" );
            
            predicateList.stream().forEach((s) -> { sbWhere.append("  and (").append( s ).append(")\n");} );
            
            if( additionalTags )
        		sbWhere.append("--W]\n");
            
            if( exprInfo.isHasWhere() ) {
                index = S.indexOfIgnoreCase( sb, "where" );
                sb.replace(index, index + "where".length(), "WHERE null is null " + sbWhere + " and " );
            }
            else {
                
                if( exprInfo.isHasOrderBy() ) {
                    index = S.indexOfIgnoreCase( sb, "order" );
                    sb.replace(index, index + "order".length(), sbWhere + " order " );
                }
                else
                    sb.append( sbWhere );
            }
		}
        
        if( !exprInfo.isHasOrderBy() && orderBy != null ) {
            
            if( additionalTags )
                sb.append("--O[\n");
            
            sb.append(" ORDER BY ").append( orderBy ).append("\n");

            if( additionalTags )
                sb.append("--O]\n"); 
        }
        
        if( sbMark.length() > 0 ) {
            index = sb.indexOf( "::MARK::" );
            if( index > 0 )
                sb.replace(index, index + "::MARK::".length(), sbMark.toString() );
        }
        
        return sb.toString( );
    }
    
    /** */
    private String buildAggr( ) {

        StringBuilder sb = new StringBuilder();

        if( additionalTags )
            sb.append("--S[\n");
        
        sb.append("SELECT ");

        if( additionalTags )
            sb.append("\n--A[\n");
            
        int i = 0;

        for( Pair<String,AggrFuncEnum> pair: aggrList ) {

            i++;

            if( i > 1 )
              sb.append(",");

            sb.append( pair.second ).append(" (").append(pair.first).append(") ").append('"').append("A$").append(pair.first).append('"');
            
        }//end for

        if( additionalTags )
            sb.append("\n--A]\n");

        boolean hasAggrTable = !S.isNullOrEmpty(this.aggrTable);

        sb.append(" from ")
//          .append( additionalTags ? "\n--Q[\n" : "\n" )
          .append( hasAggrTable ? aggrTable : "( " + select );
//          .append( additionalTags ? "\n--Q]\n" : "\n" );
        
        //
		if( predicateList != null ) {
            
            StringBuilder sbWhere = new StringBuilder( );

            sbWhere.append( additionalTags ? "--W[\n" : " ");

            if( !exprInfo.isHasWhere() )
                sbWhere.append( "WHERE null is null\n" );

            if( predicateList != null )
                predicateList.stream().forEach((s) -> { sbWhere.append("  and (").append( s ).append(")\n");} );
            
            if( additionalTags )
        		sbWhere.append("--W]\n");
            
            sb.append( sbWhere );
		}

		if(!hasAggrTable )
            sb.append(") arslt \n");
        
        if( additionalTags )
            sb.append("--S]\n");

//        if( orderBy != null ) {
//            
//            if( additionalTags )
//                sb.append("--O[\n");
//            
//            sb.append(" ORDER BY ").append( orderBy ).append("\n");
//
//            if( additionalTags )
//                sb.append("--O]\n"); 
//        }
        
        return sb.toString( );
    }
    
    /** */
    @Override
    public String build( ) {
        return aggrList != null ? buildAggr( ) : buildSingle( );
    }
    
    /** */
    public static void main( String[] args ) {
        
        try {
            
            String strSQL = "select operlist.Get_StateInfo (?, ?, level) as cInfo from dual x connect by level < ?";
            
            SQLParser sp  = new SQLParser( strSQL );
            ExprInfo exprInfo  = sp.analyze( );
            
            SimpleSelectBuilder ssb = new SimpleSelectBuilder(strSQL, exprInfo );
            //ssb.additionalTags(true);
            //ssb.mark("select 1 from fx_mrk");
            //ssb.addPredicat("sysdate='11.12.2016'");
            ssb.addAggregator("*", AggrFuncEnum.COUNT, null );
            ssb.aggrTable("cur");
        
            System.out.println( ssb.build() );
            
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public String getFromObj() {
        return S.isNullOrEmpty( this.queryAlias ) ? exprInfo.getTableFrom() : this.queryAlias;
    }
    
}
