package ru.inversion.db.expr.impl;

import ru.inversion.db.expr.SQLParameterException;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import javax.persistence.ParameterMode;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 * @author ssu
 */
public class ParameterMetadataBuilder {

    private String name;
    private String positionAsStr;
    private String sqlType;
    private String modeAsStr;
    private String defaultValue;        
    private String javaTypeAsStr;        
    private String elementType;
    private String logValue;
    private int    position = -1;
    private Class  javaType = null;
    private ParameterMode mode = null;
    
    /** */
    public ParameterMetadataBuilder( ) {
    }
    
    /** */
    public ParameterMetadataBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder position(String s) {
        this.positionAsStr = s;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder position(int position) {
        this.position = position;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder sqlType(String sqlType) {
        this.sqlType = sqlType;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder mode(String s) {
        this.modeAsStr = s;
        return this;
    }
    /** */
    public ParameterMetadataBuilder mode(ParameterMode mode) {
        this.mode = mode;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder defaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder javaType(String s) {
        this.javaTypeAsStr = s;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder javaType(Class javaType) {
        this.javaType = javaType;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder elementType(String elementType) {
        this.elementType = elementType;
        return this;
    }

    /** */
    public ParameterMetadataBuilder logValue(String logValue) {
        this.logValue = logValue;
        return this;
    }

    /** */
    public ParameterMetadata build( ) throws SQLParameterException {
        
        try {
        
            if( S.isNullOrEmpty(name) && S.isNullOrEmpty(positionAsStr) )
                throw new SQLParameterException( Tags.PRODUCT_LABEL + "Must specify the name or the position parameter");

            int parameterPosition = this.position;

            if( parameterPosition == - 1 && S.isNullOrEmpty(name) )
                parameterPosition = Integer.parseInt(positionAsStr);

            ParameterMode parameterMode = this.mode;

            if( parameterMode == null && !S.isNullOrEmpty(modeAsStr) )
                parameterMode = ParameterMode.valueOf(modeAsStr);
            else
                parameterMode = ParameterMode.IN;
                        
            Class parameterJavaType = this.javaType;

            if( parameterJavaType == null && !S.isNullOrEmpty(javaTypeAsStr) ) {

                if( javaTypeAsStr.indexOf('.') != -1 ) {
                    try {
                        parameterJavaType = Class.forName(javaTypeAsStr);
                    }
                    catch( Throwable th ) {
                        throw new SQLParameterException(Tags.PRODUCT_LABEL + "Incorrect java type '" + javaTypeAsStr + "'", th );
                    }
                }
                else if( javaTypeAsStr.equalsIgnoreCase("long") )
                    parameterJavaType = Long.class;
                else if( ( javaTypeAsStr.length() == 3 && javaTypeAsStr.equalsIgnoreCase("int") ) || javaTypeAsStr.equalsIgnoreCase("integer") )
                    parameterJavaType = Integer.class;
                else if( javaTypeAsStr.equalsIgnoreCase("BigDecimal") )
                    parameterJavaType = BigDecimal.class;
                else if( javaTypeAsStr.equalsIgnoreCase("Date") )
                    parameterJavaType = Date.class;
                else if( javaTypeAsStr.equalsIgnoreCase("LocalDate") )
                    parameterJavaType = LocalDate.class;
                else if( javaTypeAsStr.equalsIgnoreCase("LocalDateTime") )
                    parameterJavaType = LocalDateTime.class;
                else if( javaTypeAsStr.equalsIgnoreCase("String") )
                    parameterJavaType = String.class;
                else if( javaTypeAsStr.equalsIgnoreCase("Boolean") )
                    parameterJavaType = Boolean.class;
            }
            
            Integer parameterSqlType = null;

            if( S.isNullOrEmpty(sqlType) && parameterMode != ParameterMode.IN )
                throw new SQLParameterException(Tags.PRODUCT_LABEL + "Not specified SQL type for the return parameter");

            if( !S.isNullOrEmpty(sqlType) ) {

                //SQLType pst = JDBCType.valueOf( sqlType );
                int pst = Jt2St.typeOf(sqlType);
                
                if( pst == Types.ARRAY && S.isNullOrEmpty(elementType) )
                    throw new SQLParameterException(Tags.PRODUCT_LABEL + "Do not set type to element of ARRAY");
                
                if( !S.isNullOrEmpty(elementType) && elementType.indexOf('.') == -1 )
                     elementType = S.prepend( "\"PUBLIC\".", elementType );
                
                parameterSqlType = pst;
            }     

            boolean lv = true;

            if( !S.isNullOrEmpty(logValue) )
                lv = !S.in( logValue.toUpperCase(), "FALSE", "OFF", "0", "NO", "NONE" );

            ParameterMetadata pmd = null;
            
            if( parameterPosition == -1 )
                pmd = new ParameterMetadata( name, parameterSqlType, parameterJavaType, parameterMode, defaultValue, true, elementType, lv );
            else
                pmd = new ParameterMetadata( parameterPosition, parameterSqlType, parameterJavaType, parameterMode, defaultValue, true, elementType, lv );

            return pmd;
            
        } catch( Throwable th ) {
            SQLParameterException.onLoadMeta( th );
        } finally {
            name          = null;
            positionAsStr = null;
            sqlType       = null;
            modeAsStr     = null;
            defaultValue  = null;        
            javaTypeAsStr = null;
            elementType   = null;
            javaType      = null;
            position      = -1;
            mode          = null;
            logValue      = null;
        }
        return null;
    }
}
