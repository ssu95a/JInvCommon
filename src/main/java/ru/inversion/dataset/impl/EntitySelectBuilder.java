package ru.inversion.dataset.impl;

import ru.inversion.dataset.SQLDataSet;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Measurer;
import ru.inversion.utils.S;

import javax.persistence.Table;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author ssu
 */
public class EntitySelectBuilder extends SelectBuilder {
    
    final private Class entityClass;
    final private TaskContext tc;


    /** */
    public EntitySelectBuilder( Class entityClass ) { 
        this.entityClass = entityClass;
        tc = null;
    }


    /** */
    public EntitySelectBuilder(Class entityClass, TaskContext tc) {
        this.entityClass = Objects.requireNonNull( entityClass, "entityClass is null" );
        this.tc = tc;
    }


    /** */
    @Override
    public String build() {
        
        if( aggrList != null && !aggrList.isEmpty() )
            return buildAggr();
        
        return buildSelect();
    }


    /** */
    private String buildAggr( ) {

        final boolean hasAlias = !S.isNullOrEmpty( queryAlias );
        
        String tn = null;
        
        if( S.isNullOrEmpty(aggrTable) ) {
            tn = hasAlias ? ((Table)entityClass.getAnnotation( Table.class ) ).name() + " " + queryAlias
                            :
                            ((Table)entityClass.getAnnotation( Table.class ) ).name();
        }
        else {
            tn = aggrTable;
        }
        
        return prepareAggrExpression( tn );
    }


    /*
    private String buildSelectWithAlias( ) {

        final boolean hasAlias = !S.isNullOrEmpty( queryAlias );

        String select = null;

        {

            String tableName = ((Table)entityClass.getAnnotation( Table.class ) ).name();

            final List<String>
                    columnList = Arrays.asList( entityClass.getMethods () )
                                       .stream()
                                       .filter( SQLDataSet.methodFilter::test )
                                       .map( (m)->SQLDataSet.columnMapper.apply( m, null, null  ) )
                                       .collect( Collectors.toList() );

            StringBuilder sbSelect = new StringBuilder();

            sbSelect.append( "SELECT * FROM ( select " );

            if( hint != null )
                sbSelect.append( " /#" ).append(hint).append("#/\n ");

            if( mark != null )
                sbSelect.append( additionalTags ? "--M[\n" : "" )
                        .append(" (").append( mark ).append(") ").append( IMarkable.MARK_COLUMN ).append(",\n")
                        .append( additionalTags ? "--M]\n": "")
                        .append( String.join(",", columnList ) )

//            columnList.forEach( (s)->sbSelect.append(s).append(','));
//            sbSelect.setCharAt( sbSelect.length() - 1, ' ' ); //delete last ','

                        .append(" from ").append( tableName );

            select = sbSelect.toString( );
        }

        StringBuilder sb = new StringBuilder();

        if( additionalTags )
            sb.append("--S[\n");

        sb.append( additionalTags ? "--Q[\n" : "" )
                .append( select ).append("\n")
                .append( additionalTags ? "--Q]\n" : "" );

        if( additionalTags )
            sb.append("--S]\n");

        if( predicateList != null ) {

            if( additionalTags )
                sb.append("--W[\n");

            sb.append("WHERE null is null\n");

            if( predicateList != null )
                predicateList.stream().forEach((s) -> { sb.append(" and (").append( s ).append(")\n");});

            if( additionalTags )
                sb.append("--W]\n");
        }

        sb.append(") ").append(queryAlias);

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
    private String buildSelect( ) {

        String select = null;

        {
            //final boolean hasAlias = !S.isNullOrEmpty( queryAlias );

            String tableName = ((Table)entityClass.getAnnotation( Table.class ) ).name();

            List<String>
                    columnList = Arrays.stream( entityClass.getMethods () )
                    .filter( SQLDataSet.methodFilter::test )
                    .map( (m)->SQLDataSet.columnMapper.apply( m, queryAlias, tc.dialect() ) )
                    .sorted()
                    .collect( Collectors.toList() );

            StringBuilder sbSelect = new StringBuilder();

            sbSelect.append( "SELECT " );

            if( hint != null )
                sbSelect.append( " /*" ).append(hint).append("*/\n ");

            if( mark != null )
                sbSelect.append( additionalTags ? "--M[\n" : "" )
                        .append(" (").append( mark ).append(") ").append( IMarkable.MARK_COLUMN ).append(",\n")
                        .append( additionalTags ? "--M]\n": "");

            columnList.forEach( (s)->sbSelect.append(s).append(','));

            sbSelect.setCharAt( sbSelect.length() - 1, ' ' ); //delete last ','

            sbSelect.append(" FROM ").append( tableName );//.append(" ").append( expressionAlias );

            if( !S.isNullOrEmpty( queryAlias ) )
                sbSelect.append(' ').append( queryAlias );

            select = sbSelect.toString( );
        }

        StringBuilder sb = new StringBuilder();

        if( additionalTags )
            sb.append("--S[\n");

        sb.append( additionalTags ? "--Q[\n" : "" )
                .append( select ).append("\n")
                .append( additionalTags ? "--Q]\n" : "" );

        if( additionalTags )
            sb.append("--S]\n");

        if( predicateList != null ) {

            if( additionalTags )
                sb.append("--W[\n");

            sb.append("WHERE null is null\n");

            if( predicateList != null )
                predicateList.stream().forEach((s) -> { sb.append(" and (").append( s ).append(")\n");});

            if( additionalTags )
                sb.append("--W]\n");
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
    @Override
    public String build( ) {

        String select = null;
        
        {
            final boolean hasAlias = !S.isNullOrEmpty( queryAlias );
            
            String tableName = ((Table)entityClass.getAnnotation( Table.class ) ).name();
            
            List<String> 
                columnList = Arrays.asList( entityClass.getMethods () )
                                    .stream()
                                    .filter((m)->!m.isAnnotationPresent(Transient.class) )
                                    .filter((Method m) -> {
                                        Column c = m.getAnnotation(Column.class);
                                        return c != null && !c.name().isEmpty();})
                                    .map( (m)->m.getAnnotation(Column.class).name() )
                                    .map( (s)->hasAlias ? queryAlias + "." + s : s )
                                    .collect( Collectors.toList() );
            
            StringBuilder sbSelect = new StringBuilder();
            
            sbSelect.append( "SELECT " );

            if( aggrList == null && hint != null )
                sbSelect.append( " /*" ).append(hint).append("* /\n ");

            if( aggrList == null && mark != null )
                sbSelect.append( additionalTags ? "--M[\n" : "" )
                        .append(" (").append( mark ).append(") ").append( IMarkable.MARK_COLUMN ).append(",\n")
                        .append( additionalTags ? "--M]\n": "");

            columnList.forEach( (s)->sbSelect.append(s).append(','));
            
            sbSelect.setCharAt( sbSelect.length() - 1, ' ' ); //delete last ','

            sbSelect.append(" FROM ").append( tableName );//.append(" ").append( expressionAlias );

            if( !S.isNullOrEmpty( queryAlias ) )
                 sbSelect.append(' ').append( queryAlias );
            
            select = sbSelect.toString( );
        }

        StringBuilder sb = new StringBuilder();
        
        if( additionalTags )
            sb.append("--S[\n");
        
        if( aggrList != null )
        {
            sb.append("SELECT\n");
            
            if( additionalTags )
                sb.append("--A[\n");

            int i = 0;
            
            for( Pair<String,AggrFuncEnum> pair: aggrList ) {
                
                i++;
                
                if( i > 1 )
                  sb.append(",");
                
                sb.append( pair.second ).append(" (").append(pair.first).append(") ").append('"').append("A$").append(pair.first).append('"');
            }//end for
            
            sb.append("\n");
            
            if( additionalTags )
                sb.append("--A]\n");
            
            sb.append(" from (\n")
              .append( additionalTags ? "--Q[\n" : "" )
              .append( select ).append("\n")
              .append( additionalTags ? "--Q]\n" : "" );
        }
        else {
            
            sb.append( additionalTags ? "--Q[\n" : "" )
              .append( select ).append("\n")
              .append( additionalTags ? "--Q]\n" : "" );

            if( additionalTags )
                sb.append("--S]\n");
        }
        
		if( predicateList != null ) {
			
            if( additionalTags )
                sb.append("--W[\n");
            
			sb.append("WHERE null is null\n");
            
            if( predicateList != null )
                predicateList.stream().forEach((s) -> { sb.append(" and (").append( s ).append(")\n");});
            
            if( additionalTags )
        		sb.append("--W]\n");
		}
        
        if( aggrList != null ) {
            
            sb.append(") arslt \n");
            
            if( additionalTags )
                sb.append("--S]\n");
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
    

    @Override
    public String getFromObj() {
        return S.isNullOrEmpty( queryAlias ) ? ((Table)entityClass.getAnnotation( Table.class ) ).name() : queryAlias;
    }

}
