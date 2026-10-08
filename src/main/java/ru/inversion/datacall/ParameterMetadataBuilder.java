package ru.inversion.datacall;

import ru.inversion.utils.Jt2St;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;

import javax.persistence.Entity;
import javax.persistence.ParameterMode;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 * @author Sulimoff
 */
public class ParameterMetadataBuilder {

    private String name;
    private String positionAsStr;
    private String sqlTypeAsStr;
    private int    sqlType = -1;
    private String modeAsStr;
    private Object defaultValue;
    private String javaTypeAsStr;        
    private String elementType;
    private Object logValue;
    private int    position = -1;
    private Class  javaType = null;
    private ParameterMode mode = null;
    private Object isReturnValue;

    /** */
    public ParameterMetadataBuilder( ) {
    }
    
    /** */
    public ParameterMetadataBuilder name( String name) {
        this.name = name;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder position( String s) {
        this.positionAsStr = s;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder position( int position) {
        this.position = position;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder sqlType( String s) {
        this.sqlTypeAsStr = s;
        return this;
    }

    /** */
    public ParameterMetadataBuilder sqlType( int i) {
        this.sqlType = i;
        return this;
    }

    /** */
    public ParameterMetadataBuilder mode( String s) {
        this.modeAsStr = s;
        return this;
    }

    /** */
    public ParameterMetadataBuilder mode( ParameterMode mode) {
        this.mode = mode;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder defaultValue( Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder javaType( String s) {
        this.javaTypeAsStr = s;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder javaType( Class javaType) {
        this.javaType = javaType;
        return this;
    }
    
    /** */
    public ParameterMetadataBuilder elementType( String elementType) {
        this.elementType = elementType;
        return this;
    }

    /** */
    public ParameterMetadataBuilder logValue( Object logValue) {
        this.logValue = logValue;
        return this;
    }

    /** */
    public ParameterMetadataBuilder isReturnValue( Object isReturnValue) {
        this.isReturnValue = isReturnValue;
        return this;
    }

    /** */
    public ParameterMetadata build( ) throws CallException {

        try {

            if( S.isNullOrEmpty(name) && S.isNullOrEmpty(positionAsStr) && position == -1  )
                throw new CallException( Tags.PRODUCT_LABEL + "Must specify the name or the position parameter");

            int parameterPosition = this.position;

            if( parameterPosition == - 1 && S.isNullOrEmpty(name) )
                parameterPosition = Integer.parseInt(positionAsStr);

            ParameterMode parameterMode = this.mode;

            if( parameterMode == null ) {

                if ( !S.isNullOrEmpty(modeAsStr) )
                    parameterMode = ParameterMode.valueOf(modeAsStr);
                else
                    parameterMode = ParameterMode.IN;
            }

            Class parameterJavaType = this.javaType;

            if( parameterJavaType == null && !S.isNullOrEmpty(javaTypeAsStr) ) {

                if( javaTypeAsStr.indexOf('.') != -1 ) {
                    try {
                        parameterJavaType = Class.forName(javaTypeAsStr);
                    }
                    catch( Throwable th ) {
                        throw new CallException(Tags.PRODUCT_LABEL + "Incorrect java type '" + javaTypeAsStr + "'", th );
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
                else if( javaTypeAsStr.equalsIgnoreCase("Char") )
                    parameterJavaType = Character.class;
            }

//            if(!S.isNullOrEmpty(elementType) && LangEnum.getGLangPL( ) != LangEnum.PL_pgSQL )
//                elementType = elementType.toUpperCase();

            Integer parameterSqlType = null;

            if( !S.isNullOrEmpty(sqlTypeAsStr) || sqlType != -1 )
            {
                //final SQLType pst;
                int pst = 0;

                if( !S.isNullOrEmpty(sqlTypeAsStr) )
                {
                    try {
                        pst = Jt2St.typeOf(sqlTypeAsStr);
                    } catch( Exception ex ) {
                        throw new CallException( Tags.PRODUCT_LABEL + "'" + sqlTypeAsStr + "' illegal name for SQL type" );
                    }
                }
                else
                {
                    try {
                        // Проверим что ИД типа нормальный передали
                        JDBCType.valueOf(sqlType);
                        pst = sqlType;
                    } catch(Exception ex) {
                        throw new CallException( Tags.PRODUCT_LABEL + "'" + sqlType + "' illegal number for SQL type", ex );
                    }
                }

                if( pst == Types.ARRAY && S.isNullOrEmpty(elementType) )
                    throw new CallException(Tags.PRODUCT_LABEL + "Do not set type to element of ARRAY");

                // Поддержка STRUCT
                if( pst == Types.STRUCT ) {

                    if( S.isNullOrEmpty(elementType) )
                        throw new CallException(Tags.PRODUCT_LABEL + "Do not set type to element of STRUCT");

                    if( parameterJavaType != null )
                    {
                        if( parameterJavaType.isPrimitive() || !parameterJavaType.isAnnotationPresent(Entity.class) ) {
                            throw new CallException(Tags.PRODUCT_LABEL + "JavaType parameter of the 'STRUCT' must be POJO class with 'Entity' annotation");
                        }
                    }// end if parameterJavaType != null
                }
/*
                if( !S.isNullOrEmpty(elementType) )
                {
                    if( LangEnum.getGLangPL( ) != LangEnum.PL_pgSQL )
                    {
                        if( elementType.startsWith(PUBLIC) || elementType.startsWith(SYS) )
                        {
                            //none;
                        }
                        else
                        {
                            if( elementType.startsWith(XXI) )
                                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Не допустимо в именах типов ссылаться на объекты БД через имя 'XXI'!");

                            elementType = S.prepend( PUBLIC, elementType );
                        }

                    }
                    else
                    {
                        //Замена типов
                        String v = g_typeNameMap.get( elementType );
                        if( v != null )
                            elementType = v;
                    }
                }
*/
                parameterSqlType = pst;
            }
            else
                if( parameterMode != ParameterMode.IN )
                    throw new CallException(Tags.PRODUCT_LABEL + "Not specified SQL type for the return parameter");

            boolean lv = true;

            if( logValue != null ) {
                if( S.isString(logValue) )
                    lv = !S.in( logValue.toString().toUpperCase(), "FALSE", "OFF", "0", "NO", "NONE" );
                else
                    lv = TypeConverter.convert( logValue, Boolean.class );
            }

            boolean rv = false;

            if( isReturnValue != null )
            {
                if( S.isString(isReturnValue) )
                    rv = !S.in( isReturnValue.toString().toUpperCase(), "FALSE", "OFF", "0", "NO", "NONE" );
                else
                    rv = TypeConverter.convert( isReturnValue, Boolean.class );
            }

            ParameterMetadata pmd = null;

            if( parameterPosition == -1 )
                pmd = new RawParameterMetadata( name, parameterSqlType, parameterJavaType, parameterMode, defaultValue, true, elementType, lv, rv );
            else
                pmd = new RawParameterMetadata( parameterPosition, parameterSqlType, parameterJavaType, parameterMode, defaultValue, true, elementType, lv, rv );

            return pmd;
            
        } catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error loading information about a parameter", th );
        } finally {
            name          = null;
            positionAsStr = null;
            sqlTypeAsStr  = null;
            modeAsStr     = null;
            defaultValue  = null;        
            javaTypeAsStr = null;
            elementType   = null;
            javaType      = null;
            position      = -1;
            mode          = null;
            logValue      = null;
            isReturnValue = null;
        }
    }
}
