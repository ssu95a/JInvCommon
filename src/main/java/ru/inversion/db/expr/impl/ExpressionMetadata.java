package ru.inversion.db.expr.impl;

import ru.inversion.db.expr.SQLExpressionException;
import ru.inversion.db.expr.SQLParameterException;
import ru.inversion.db.expr.SQLParameterNotFoundException;
import ru.inversion.db.work.DBParametersWork;
import ru.inversion.utils.Tags;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author ssu
 */
public class ExpressionMetadata {

    final private String ID;
    
    final private String preparedSqlText;
    
    final private Map<String, ParameterMetadata> metaParameterNamedMap;
    final private List<ParameterMetadata>        metaParameterIndexList;
    
    final private boolean isStoredProc;

    /** */
    public ExpressionMetadata( String ID, String preparedSqlText,
                               Map<String, ParameterMetadata> metaParameterNamedMap, List<ParameterMetadata> metaParameterIndexList )
    {
        this( ID, preparedSqlText, metaParameterNamedMap, metaParameterIndexList, false );
    }

    /** */
    public ExpressionMetadata( String ID, String preparedSqlText,
                               Map<String, ParameterMetadata> metaParameterNamedMap, List<ParameterMetadata> metaParameterIndexList, boolean isStoredProc )
    {
        this.ID                    = ID;
        this.metaParameterNamedMap = metaParameterNamedMap;
        this.metaParameterIndexList= metaParameterIndexList;
        this.isStoredProc          = isStoredProc;

        final StringBuilder sb = new StringBuilder();

        if( metaParameterNamedMap != null )
            metaParameterNamedMap.forEach(( s, p ) -> {
                if( !p.isLogValue() )
                     p.getJDBCStatementIndexes().forEach( (i)->sb.append(i).append(',') );
            });

        if( metaParameterIndexList != null )
            metaParameterIndexList.forEach(( p ) -> {
                if( !p.isLogValue() )
                     p.getJDBCStatementIndexes().forEach( (i)->sb.append(i).append(',') );
            });

        if( sb.length() > 0 ) {
            sb.deleteCharAt( sb.length() - 1 );
            sb.insert( 0, "\n--lhv:" );
            sb.insert( 0, preparedSqlText );

            this.preparedSqlText = sb.toString();
        }
        else
            this.preparedSqlText = preparedSqlText;

    }

    /** */
    public String getID( ) {
        return ID;
    }

    /** */
    public Map<String, ParameterMetadata> getMetaParameterNamedMap() {
        return Collections.unmodifiableMap( metaParameterNamedMap );
    }

    /** */
    public DBParametersWork prepareForExecute( ) throws SQLExpressionException {
        return isStoredProc ?
               new SpWork( preparedSqlText, null ) :
               new StWork( preparedSqlText, null, metaParameterNamedMap, metaParameterIndexList );
    }
    
    /** */
    public DBParametersWork prepareForExecute( Connection connection ) throws SQLExpressionException {
        
        Objects.requireNonNull(connection, Tags.PRODUCT_LABEL + "Connection is null" );

        return isStoredProc ?
               new SpWork( preparedSqlText, connection ) :
               new StWork( preparedSqlText, connection, metaParameterNamedMap, metaParameterIndexList );

        //return new StWork( preparedSqlText, connection, metaParameterNamedMap, metaParameterIndexList );
    }
    
    /*
    public String getSqlText( ) {
        return preparedSqlText;
    }
    */

    /** */
    private class SpWork extends AbstractWorkBase {
        
        /** */
        public SpWork( String sqlText, Connection connection ) 
            throws SQLExpressionException
        {
            super( sqlText, connection, null, metaParameterIndexList );
        }

        /** */
        @Override
        protected void checkParametersBeforeExcecute( ) throws SQLParameterException {
            ;
        }
        
        /** */
        @Override
        public Object getParameter( String parameterName ) {
            
            ParameterMetadata pm = metaParameterNamedMap.get( parameterName );
            
            if( pm != null )
                return super.getParameter( pm.getPosition() );
            else
                throw new IllegalArgumentException( "parameterName", new SQLParameterNotFoundException(parameterName) );
        }
        
        /** */
        @Override
        public void setParameter( String parameterName, Object value) {
            
            ParameterMetadata pm = metaParameterNamedMap.get( parameterName );
            
            if( pm != null )
                super.setParameter( pm.getPosition(), value );
            else
                throw new IllegalArgumentException( "parameterName", new SQLParameterNotFoundException(parameterName) );
        }
        
        /** */
        @Override
        public void getParametersMap( Map<String, Object> valueMap, boolean onlyDef ) {

            if( metaParameterNamedMap.isEmpty() || valueMap == null )
                return;
            
            for( Map.Entry<String, ParameterMetadata> e : metaParameterNamedMap.entrySet() ) {
                
                String key = e.getKey();
                
                if( !onlyDef || valueMap.containsKey(key) ) {
                    
                    Object value = getParameter(e.getValue().getPosition());
                    
                    valueMap.put( key, value );
                
                }//end if
                
            }//end for
        }
    }    
}
