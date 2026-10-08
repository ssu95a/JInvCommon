package ru.inversion.datacall;

import javax.persistence.ParameterMode;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;


/**
 * Метаданные одного параметра внутри хранимого кода
 * <p>
 * @author Sulimoff
 */
class RawParameterMetadata implements ParameterMetadata<Object>{

    final private Comparable<?> identifier;

    final private Integer       sqlType;
    final private ParameterMode mode;
    final private Object        defaultValue;
    final private Class<?>      javaType;
    final private String        rawElementType;
    final private boolean       logValue;
    final private boolean       isReturnValue;

    /** */
    RawParameterMetadata( String name, Integer sqlType, Class<Object> javaType, ParameterMode mode, Object defaultValue, boolean isDefInParameterList, String rawElementType, boolean logValue, boolean isReturnValue ) {
        this.identifier     = Objects.requireNonNull( name, "'name' is null");
        this.isReturnValue  = isReturnValue;
        this.sqlType        = sqlType;
        this.mode           = mode;
        this.defaultValue   = defaultValue;
        this.rawElementType = rawElementType;

        if( javaType == null && sqlType != null )
            this.javaType = getJavaType(sqlType);
        else
            this.javaType = javaType;

        this.logValue     = logValue;
    }
    
    /** */
    RawParameterMetadata(Integer position, Integer sqlType, Class<Object> javaType, ParameterMode mode, Object defaultValue, boolean isDefInParameterList, String rawElementType, boolean logValue, boolean isReturnValue ) {
        this.identifier     = Objects.requireNonNull(position, "'position' is null");
        this.sqlType        = sqlType;
        this.mode           = mode;
        this.defaultValue   = defaultValue;
        this.rawElementType = rawElementType;
        this.isReturnValue  = isReturnValue;

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
        return identifier instanceof Integer;
    }
    
    /** */
    @Override
    public String getName() {
        return isIndexed( ) ? null : (String)identifier;
    }

    /** */
    @Override
    public Integer getPosition() {
        return isIndexed() ? (Integer)identifier : null;
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
    public Class<Object> getParameterType() {
        return (Class<Object>) javaType;
    }

    /** */
    String getRawElementType() {
        return rawElementType;
    }

    /** */
    public String getElementType( ) {
        return getRawElementType();
    }

    /** */
    public boolean isLogValue() { return logValue; }

    /** */
    public boolean isReturnValue( ) {
        return isReturnValue;
    }

}
