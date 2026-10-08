package ru.inversion.db.expr.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.persistence.NamedStoredProcedureQueries;
import javax.persistence.NamedStoredProcedureQuery;
import static javax.persistence.ParameterMode.*;
import javax.persistence.StoredProcedureParameter;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.S;

/**
 *
 * @author ssu
 */
public class SPAnnotClassLoader {

    final private List<ExpressionMetadata> expressionList = new ArrayList<>();
    final private Class   annotClass;
    
    /** */
    public SPAnnotClassLoader( Class annotClass ) {
        this.annotClass = Objects.requireNonNull( annotClass, "annotClass is null" );
    }
    
    /** */
    public List<ExpressionMetadata> parse( ) {

        NamedStoredProcedureQuery nspq = (NamedStoredProcedureQuery)annotClass.getAnnotation( NamedStoredProcedureQuery.class );

        if( nspq != null )
            expressionList.add( parseAnnot(nspq) );

        NamedStoredProcedureQueries nspqs = (NamedStoredProcedureQueries) annotClass.getAnnotation( NamedStoredProcedureQueries.class );

        if( nspqs != null ) {

            for( NamedStoredProcedureQuery n : nspqs.value() )
                 expressionList.add( parseAnnot(n) );
        }//end if
        
        return expressionList;
    }
    
    /** */
    private ExpressionMetadata parseAnnot( NamedStoredProcedureQuery nspq ) {
        
        Map<String, ParameterMetadata> metaParameterNamedMap = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        List<ParameterMetadata> metaParameterList = new ArrayList<>();
        
        StringBuilder sb = new StringBuilder();
        
        int index = 0;
        
        if( nspq.resultClasses().length == 0 )
            sb.append("{call ");
        else 
        {
            sb.append("{?=call ");
            
            Class retClass = nspq.resultClasses()[0];
            
            ParameterMetadata pm = new ParameterMetadata( index, Jt2St.get(retClass), retClass, OUT, null, true, null, true );
            pm.addJDBCStatementIndex( index );
            metaParameterList.add( pm );
            metaParameterNamedMap.put( "return#", pm );

            index++;
        }
        
        sb.append( nspq.procedureName() ).append("(");
        
        StoredProcedureParameter[] ps = nspq.parameters();

        boolean first = true;
        
        if( ps != null && ps.length > 0 ) {
            
            for( StoredProcedureParameter p : ps ) {
            
                if( !first )
                    sb.append(", ");
                
                ParameterMetadata pm = new ParameterMetadata( index, Jt2St.get( p.type() ), p.type(), p.mode(), null, true, null, true );
                pm.addJDBCStatementIndex( index );
                
                index++;
                
                metaParameterList.add( pm );
                
                if( S.isNotNullOrEmpty(p.name() ) )
                    metaParameterNamedMap.put( p.name(), pm );
                
                sb.append("?");
                
                first = false;
            }//end for
        }
        
        sb.append(")}");
        
        return new ExpressionMetadata( nspq.name(), sb.toString(), metaParameterNamedMap, metaParameterList, true );    
    }
}
