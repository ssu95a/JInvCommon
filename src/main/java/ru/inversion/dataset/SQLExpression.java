package ru.inversion.dataset;

import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.util.*;

/**
 *
 * @author ssu @
 */
public class SQLExpression implements ISQLExpression {

	private String strSQL;
	private Map<Integer,Object> parameterIndexMap;
	private Map<String, Object>	parameterNamedMap;

	public SQLExpression( ) {
	}

    public SQLExpression( String strSQL, Object ... parameters ) {
        this.strSQL = strSQL;
        if( parameters != null && parameters.length > 0 )
            U.forEachWithIndex( U.toIterator(parameters), (v,i)->this.setParameter(i,v) );
    }

    public SQLExpression( String strSQL) {
		this.strSQL = strSQL;
	}
	
	@Override
	public String getSQL() {
		return strSQL;
	}
	public void setSQL( String strSQL ) {
		this.strSQL = strSQL;
	}
	
    /** */
	@Override
	public Object getParameter( int index ) {
		return parameterIndexMap == null ? null : parameterIndexMap.get(index);
	}
	
    /** */
    @Override
	public void setParameter( int index, Object value ) {
		if( parameterIndexMap == null )
			parameterIndexMap = new TreeMap<>();
		parameterIndexMap.put( index, value );
	}

    /** */
	public void setParameters( List<Object> parameters ) {
		
        if( parameters == null || parameters.isEmpty() )
            return;
        
        if( parameterIndexMap == null )
			parameterIndexMap = new TreeMap<>();
        
        U.forEachWithIndex( parameters, ( v, i) -> parameterIndexMap.put(i, v) );
	}
	
    /** */
	@Override
	public Object getParameter( String parameterName ) {
		return parameterNamedMap == null ? null : parameterNamedMap.get(parameterName);
	}
    
	/** */
    @Override
	public void setParameter( String parameterName, Object value ) {
		
        if( parameterNamedMap == null ) {
			parameterNamedMap = new HashMap<>();
		}
        
		parameterNamedMap.put( parameterName, value );
	}
    
	/** */
	public void setParameters( Map<String,Object> parameters ) {
        
        if( parameters == null || parameters.isEmpty() )
            return;
        
		if( parameterNamedMap == null ) {
			parameterNamedMap = new HashMap<>();
		}
        
		parameterNamedMap.putAll(parameters);
	}

    /** */
    @Override
    public String getCompletedSQL() {

        if( S.isNullOrEmpty(strSQL) )
            return S.EMPTY_STRING;
        
        SQLParser sp = new SQLParser(strSQL);
        
        return sp.prepareSqlWithParametersValue(this);
    }

    /** */
    @Override
    public boolean hasParameter( String parameterName ) {
        return parameterNamedMap != null && parameterNamedMap.containsKey(parameterName );
    }

    /** */
    @Override
    public boolean hasParameter( int parameterIndex  ) {
        return parameterIndexMap != null && parameterIndexMap.containsKey(parameterIndex);
    }

    /** */
    @Override
    public void getParametersList( List<Object> valueList ) {
        
        if( valueList != null && parameterIndexMap != null ) {
            parameterIndexMap.forEach((Integer t, Object u) -> { valueList.add(u); });
        }
    }

    /** */
    @Override
    public List<Object> getParametersList( ) {
        
        List<Object> retList = new ArrayList<>( );
        
        getParametersList(retList);
        
        return retList;
    }

    @Override
    public void getParametersMap(Map<String, Object> valueMap, boolean onlyDef) {

        if( parameterNamedMap == null || valueMap == null )
            return;
        
        if( onlyDef )
            parameterNamedMap.forEach( (String t, Object u) -> { if( valueMap.containsKey(t) ) valueMap.put( t, u ); });
        else 
            valueMap.putAll(parameterNamedMap);
    }

    @Override
    public Map<String, Object> getParametersMap() {
        return parameterNamedMap;
    }

}
