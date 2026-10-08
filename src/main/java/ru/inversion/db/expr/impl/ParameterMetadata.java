package ru.inversion.db.expr.impl;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.persistence.Parameter;
import javax.persistence.ParameterMode;
import ru.inversion.utils.Jt2St;

/**
 * 
 * @author ssu
 */
public class ParameterMetadata implements Parameter {
    
    final private String        name;
    final private Integer       position;
    final private Integer       sqlType;
    final private ParameterMode mode;
    final private Object        defaultValue;
    final private Class         javaType;
    final private List<Integer> indexList = new ArrayList<>();
    final private boolean       isDefInParameterList;
    final private String        elementType;
    final private boolean       logValue;

    /** */
    public ParameterMetadata( String name, Integer sqlType, Class javaType, ParameterMode mode, Object defaultValue, boolean isDefInParameterList, String elementType, boolean logValue ) {
        this.name     = name;
        this.position = null;
        this.sqlType  = sqlType;
        this.mode                 = mode;
        this.defaultValue         = defaultValue;
        this.isDefInParameterList = isDefInParameterList;
        this.elementType          = elementType;  
        
        if( javaType == null && sqlType != null ) 
            this.javaType = getJavaType(sqlType);
        else
            this.javaType = javaType;

        this.logValue = logValue;
    }
    
    /** */
    public ParameterMetadata( Integer position, Integer sqlType, Class javaType, ParameterMode mode, Object defaultValue, boolean isDefInParameterList, String elementType, boolean logValue ) {
        this.name                 = null;
        this.position             = position;
        this.sqlType              = sqlType;
        this.mode                 = mode;
        this.defaultValue         = defaultValue;
        this.isDefInParameterList = isDefInParameterList;
        this.elementType          = elementType;  

        if( javaType == null && sqlType != null ) 
            this.javaType = getJavaType(sqlType);
        else
            this.javaType = javaType;

        this.logValue = logValue;
    }

    /** */
    static private Class getJavaType( int sqlType ) {
        
        switch( sqlType ) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
                return String.class;
            case Types.NUMERIC:
            case Types.DECIMAL:
                return BigDecimal.class;
            case Types.BIT:
                return Boolean.class;
            case Types.TINYINT:
                return Byte.class;
            case Types.SMALLINT:
                return Short.class;
            case Types.INTEGER:
                return Integer.class;
            case Types.BIGINT:
                return Long.class;
            case Types.REAL:
            case Types.FLOAT:
                return Float.class;
            case Types.DOUBLE:
                return Double.class;
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return byte[].class;
            case Types.DATE:
                return LocalDate.class;
            case Types.TIME:
                return LocalTime.class;
            case Types.TIMESTAMP:
                return LocalDateTime.class;
        }
        return null;    
    }
    
    /** */
    public boolean isIndexed( ) {
        return name == null;
    }
    
    /** */
    @Override
    public String getName() {
        return name;
    }

    /** */
    @Override
    public Integer getPosition() {
        return position;
    }

    /** */
    public Integer getSQLType() {
        return sqlType;
    }

    /** */
    public ParameterMode getMode() {
        return mode;
    }
    
    /** */
    public Object getDefaultValue() {
        return defaultValue;
    }
    
    /** */
    @Override
    public Class getParameterType() {
        return javaType;
    }
    
    /** */
    public List<Integer> getJDBCStatementIndexes( ) {
        return Collections.unmodifiableList( indexList );
    }
    
    /** */
    public void addJDBCStatementIndex( int index ) {
        indexList.add(index+1);
    }
    
    /** Признак */
    public boolean isDefInParameterList() {
        return isDefInParameterList; 
    }
    
    /** */
    public String getElementType() {
        return elementType;
    }

    /** */
    public boolean isLogValue() { return logValue; }
}
