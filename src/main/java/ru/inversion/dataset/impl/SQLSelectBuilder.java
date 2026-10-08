package ru.inversion.dataset.impl;

import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.utils.S;

/**
 *
 * @author ssu
 */
public class SQLSelectBuilder extends SelectBuilder {
    
    private String  select;
    
    /** */
    public SQLSelectBuilder( ) {
        this.queryAlias = "qrslt";
    }
    
    /** */
    public SQLSelectBuilder( String select ) {
        this.select = select;
        this.queryAlias = "qrslt";
    }
    
    /** */
    public SQLSelectBuilder select( String select ) {
        this.select = select;
        return this;
    }
    
    /** */
    public void clear( ) {
        super.clear();
        select = null; 
    }

    /** */
    @Override
    public String build() {
        
        if( aggrList != null && !aggrList.isEmpty() )
            return buildAggr();
        
        return buildSelect();
    }
    
    /** */
    public String buildAggr( ) {

        return prepareAggrExpression( 
                S.isNullOrEmpty(aggrTable) ? 
                        "( " + select + ") " + queryAlias
                        : 
                        aggrTable 
        );
    }
    
    /** */
    public String buildSelect( ) {
 
        StringBuilder sb = new StringBuilder();

        if( additionalTags )
            sb.append("--S[\n");
        
        sb.append("SELECT\n");

        if( hint != null )
            sb.append( " /*" ).append(hint).append("*/ ");

        if( mark != null )
            sb.append( additionalTags ? "--M[\n" : "" )
              .append(" (").append( mark ).append(") ").append( IMarkable.MARK_COLUMN ).append(",\n")
              .append( additionalTags ? "--M]\n": "");
        
        String alias = S.isNullOrEmpty(queryAlias) ? "qrslt" : queryAlias;
        
        sb.append(" ").append(alias).append(" .* ");
        
        sb.append(" from (")
          .append( additionalTags ? "--Q[\n" : "" )
          .append( select )
          .append( additionalTags ? "--Q]\n" : "" )
          .append(") ").append( alias ).append(" \n");
        
        if( additionalTags )
            sb.append("--S]\n");
        
        //
		if( predicateList != null ) {
            
            StringBuilder sbWhere = new StringBuilder( );

            if( additionalTags )
                sbWhere.append("--W[\n");

            int indexWhere = sb.indexOf("<w>");
            
            if( indexWhere == -1 )
                sbWhere.append( "WHERE null is null\n" );
            
            if( predicateList != null )
                predicateList.stream().forEach((s) -> { sbWhere.append("  and (").append( s ).append(")\n");} );
            
            if( additionalTags )
        		sbWhere.append("--W]\n");
            
            if( indexWhere == -1 )
                sb.append( sbWhere );
            else 
                sb.replace( indexWhere, indexWhere + 3, sbWhere.toString() );
		}
        
        if( orderBy != null ) {
            
            if( additionalTags )
                sb.append("--O[\n");
            
            sb.append(" ORDER BY ").append( orderBy ).append("\n");

            if( additionalTags )
                sb.append("--O]\n"); 
        }
        
        clear( );
        
        return sb.toString();
    }

    
    /**
    public String build( ) {
 
        StringBuilder sb = new StringBuilder();

        if( additionalTags )
            sb.append("--S[\n");
        
        sb.append("SELECT\n");

        if( aggrList == null && hint != null )
            sb.append( " /*" ).append(hint).append("* / ");

        if( aggrList == null && mark != null )
            sb.append( additionalTags ? "--M[\n" : "" )
              .append(" (").append( mark ).append(") ").append( IMarkable.MARK_COLUMN ).append(",\n")
              .append( additionalTags ? "--M]\n": "");
        
        String alias = S.isNullOrEmpty(queryAlias) ? "qrslt" : queryAlias;
        
        if( aggrList == null )
            sb.append(" ").append(alias).append(" .* ");
        else 
        {
            if( additionalTags )
                sb.append("--A[\n");
            
            int i = 0;
            
            for( Pair<String,AggrFuncEnum> pair: aggrList ) {
                
                i++;
                
                if( i > 1 )
                  sb.append(",");
                
                sb.append( pair.second ).append(" (").append(pair.first).append(") ").append("A$").append(pair.first);
            }//end for

            sb.append("\n");
            
            if( additionalTags )
                sb.append("--A]\n");
            
        }//end else
        
        sb.append(" from (")
          .append( additionalTags ? "--Q[\n" : "" )
          .append( select )
          .append( additionalTags ? "--Q]\n" : "" )
          .append(") ").append( alias ).append(" \n");
        
        if( additionalTags )
            sb.append("--S]\n");
        
        //
		if( predicateList != null ) {
            
            StringBuilder sbWhere = new StringBuilder( );

            if( additionalTags )
                sbWhere.append("--W[\n");

            int indexWhere = sb.indexOf("<w>");
            
            if( indexWhere == -1 )
                sbWhere.append( "WHERE null is null\n" );
            
            if( predicateList != null )
                predicateList.stream().forEach((s) -> { sbWhere.append("  and (").append( s ).append(")\n");} );
            
            if( additionalTags )
        		sbWhere.append("--W]\n");
            
            if( indexWhere == -1 )
                sb.append( sbWhere );
            else 
                sb.replace( indexWhere, indexWhere + 3, sbWhere.toString() );
		}
        
        if( orderBy != null ) {
            
            if( additionalTags )
                sb.append("--O[\n");
            
            sb.append(" ORDER BY ").append( orderBy ).append("\n");

            if( additionalTags )
                sb.append("--O]\n"); 
        }
        
        clear( );
        
        return sb.toString();
    }
    */

    /** */
    public static void main( String[] args ) {
        
        try {
            
//            SQLSelectBuilder ssb = new SQLSelectBuilder("select operlist.Get_StateInfo (?, ?, level) as cInfo from dual connect by level < ?" );
//            //ssb.additionalTags(true);
//            ssb.mark("select 1 from fx_mrk");
//            ssb.addPredicat("sysdate='11.12.2016'");
//            ssb.queryAlias("ALIAS");
//            ssb.addAggregator("1", AggrFuncEnum.COUNT );
//            ssb.aggrTable("trn");
//
//            System.out.println( ssb.build() );

            String orderBy = "~replace (cTrnPurp, '~~', '~')~";

            if( S.isNullOrEmpty(orderBy) )
                return;

            if( orderBy.indexOf('~') == -1 )
                return;

            boolean insideStr = false;
            char    ch;

            StringBuilder sb = new StringBuilder( orderBy.length() );

            for( int i = 0; i < orderBy.length(); i++ ) {

                ch = orderBy.charAt(i);

                if( ch == "'".charAt(0) )
                    insideStr = !insideStr;

                if( ch == '~' && !insideStr )
                    continue;

                sb.append( ch );
            }

            System.out.println( sb.toString() );

        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public String getFromObj() {
        return S.isNullOrEmpty(queryAlias) ? "qrslt" : queryAlias;
    }
    
    
}
