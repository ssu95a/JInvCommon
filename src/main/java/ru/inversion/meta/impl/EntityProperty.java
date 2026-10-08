package ru.inversion.meta.impl;

import ru.inversion.dataset.SQLDataSet;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.entity.ContentTypeEnum;
import ru.inversion.db.entity.ProxyFor;
import ru.inversion.db.entity.RegisterEnum;
import ru.inversion.meta.EntityPropertyException;
import ru.inversion.meta.IEntityColumn;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TCStorage;
import ru.inversion.utils.IDumpable;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.bits.Bits8;
import ru.inversion.utils.converter.TypeConverter;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Одно свойство бизнес-сущности
 * @author ssu
 */
public class EntityProperty<P,R> implements IEntityProperty<P,R> {

    /** Связанный со свойством столбец в таблице БД */
    private class EntityColumn implements IEntityColumn<P,R> {

        final private String name, sequenceName, table;
        final private Bits8 flags = new Bits8();

        final Object queryExpression;

        public EntityColumn( String columnName, Map<String,Object> values )
        {
            this.name            = columnName;
            this.table           = (String)values.get("column.table");
            this.sequenceName    = (String)values.get("sequence.db_name" );
            this.queryExpression = values.get("column.query_expr");

            flags.set( 0, (Boolean)values.getOrDefault("column.insertable", Boolean.FALSE ) );              // 0 this.insertable = (Boolean)values.getOrDefault("column.insertable", Boolean.FALSE );
            flags.set( 1, (Boolean)values.getOrDefault("column.updatable" , Boolean.FALSE ) );              // 1 this.updatable         = (Boolean)values.getOrDefault("column.updatable" , Boolean.FALSE );
            flags.set( 2, (Boolean)values.getOrDefault("column.returning.insert", Boolean.FALSE ) );        // 2 this.returningOnInsert = (Boolean)values.getOrDefault("column.returning.insert", Boolean.FALSE );
            flags.set( 3, (Boolean)values.getOrDefault("column.returning.sequence_value", Boolean.FALSE ) );// 3 this.sequenceValue     = (Boolean)values.getOrDefault("column.returning.sequence_value", Boolean.FALSE );
            flags.set( 4, (Boolean)values.getOrDefault("column.returning.update", Boolean.FALSE ) );        // 4 this.returningOnUpdate = (Boolean)values.getOrDefault("column.returning.update", Boolean.FALSE );
            flags.set( 5, (Boolean)values.getOrDefault("column.nullable", Boolean.TRUE  ) );                // 5 this.nullable          = (Boolean)values.getOrDefault("column.nullable", Boolean.TRUE  );
            flags.set( 6, (Boolean)values.getOrDefault("column.unique"  , Boolean.FALSE ) );                // 6 this.unique            = (Boolean)values.getOrDefault("column.unique",   Boolean.FALSE );
        }

        /** */
        @Override
        public String getName() {
            return name;
        }
        /** */
        @Override
        public boolean isInsertable() {
            return flags.get(0);
        }
        /** */
        @Override
        public boolean isUpdatable() {
            return flags.get(1);
        }
        /** */
        @Override
        public boolean isReturningOnInsert() { return flags.get(2); }
        /** */
        @Override
        public boolean isSequenceValue() {
            return flags.get(3);
        }
        /** */
        @Override
        public boolean isReturningOnUpdate() {
            return flags.get(4);
        }

        @Override
        public IEntityProperty<P,R> getProperty() {
            return EntityProperty.this;
        }
        /** */
        @Override
        public int getSQLType() { return Jt2St.get( getProperty().getType() );}
        /** */
        @Override
        public boolean isNullable() { return flags.get(5);}

        @Override
        public String getSequenceName() {
            return sequenceName;
        }

        @Override
        public String getTableName() {
            return table;
        }

        @Override
        public Object getQueryExpr() {
            return queryExpression;
        }

        @Override
        public String makeQueryExpr( SqlDialect dialect )
        {
            final Object queryExpr = getQueryExpr();

            if( queryExpr == null )
                return getColumnName();

            if( queryExpr instanceof String )
                return queryExpr + " " + getColumnName();
            else
            {
                final Function<LangEnum,String> qes = (Function<LangEnum,String>)queryExpr;
                return qes.apply( dialect.plLang() ) + " " + getColumnName();
            }
        }

        public boolean isUnique() {
            return flags.get(6);
        }

        @Override
        public void dump( Map<String, Object> properties ) {

            if( properties == null )
                return;

            properties.put( "name",  getName() );
            properties.put( "table", getTableName() );
            properties.put( "unique",isUnique() );

            properties.put( "insertable",     isInsertable() );
            properties.put( "returnOnInsert", isReturningOnInsert() );
            properties.put( "sequenceValue",  isSequenceValue() );
            properties.put( "sequenceName",   getSequenceName() );

            properties.put( "updatable", isUpdatable() );
            properties.put( "returnOnUpdate", isReturningOnInsert() );

            properties.put( "queryExpression", getQueryExpr() );
        }

    }//end of EntityColumn

          private boolean            isId;
    final private String             name, orderBy, propertyName;
    final private PropertyDescriptor pd;
    final private ContentTypeEnum    contentType;
          private IEntityColumn<P,R> entityColumn;
          private boolean            isTransient,
                                     isRequired;
    final private int                length;

    private IEntityProperty<P,?>     proxyFor;

    final private RegisterEnum       caseSensitiveSearchMode;
    final private boolean            decimalStripTrailingZeros;
    final private boolean            useInLOVPredicate;

    /** */
    EntityProperty( String propertyName, String name, PropertyDescriptor pd, Map<String,Object> values  ) {
        this.isId        = values.containsKey("id");// ;pd.getValue("id") != null;
        this.propertyName= propertyName;
        this.name        = name;
        this.pd          = pd;
        this.contentType = (ContentTypeEnum)values.get("content");
        this.isTransient = values.containsKey("transient"); //pd.getValue("transient") != null;
        if( isTransient )
            entityColumn = null;
        else
            entityColumn = new EntityColumn( name, values );

        if( entityColumn != null )
            this.isRequired = !entityColumn.isNullable() || values.containsKey("required");
        else
            this.isRequired = values.containsKey("required"); // pd.getValue("required") != null;

        this.length  = (Integer)values.getOrDefault("column.length",-1);

        this.orderBy = (String)values.get("order_by");//pd.getValue("order_by");

        this.caseSensitiveSearchMode   = (RegisterEnum)values.getOrDefault( "attribute.caseSensitiveSearchMode",   RegisterEnum.UNKNOWN_CASE );
        this.decimalStripTrailingZeros = (Boolean)     values.getOrDefault( "attribute.decimalStripTrailingZeros", Boolean.FALSE );
        this.useInLOVPredicate         = "ROWID".equalsIgnoreCase(name) ? Boolean.FALSE : (Boolean)values.getOrDefault( "attribute.useInLOVPredicate", Boolean.TRUE );
    }

    /** */
    @Override
    public boolean isId() {
        return isId;
    }

    /** */
    @Override
    public String getPropertyName() { return propertyName; }

    /** */
    @Override
    public String getName() {
        return name;
    }

    /** */
    @Override
    @SuppressWarnings("unchecked")
    public Class<R> getType() {
        return (Class< R >)pd.getPropertyType();
    }

    /** */
    @Override
    public ContentTypeEnum getContent() {
        return contentType;
    }

    /** */
    @Override
    public IEntityColumn<P,R> getColumnInfo( ) {
        return entityColumn;
    }

    /** */
    @Override
    public boolean isTransient() {
        return isTransient;
    }

    /** */
    @Override
    public void invokeSetter( P pojo, Object param ) {

        try {

            if( isReadOnly() )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Property '" + getPropertyName() + "' is read only." );

            Objects.requireNonNull( pojo, Tags.PRODUCT_LABEL + "Parameter 'pojo' is null");

            Object value = TypeConverter.convert( param, getType() );

            pd.getWriteMethod().invoke( pojo, value );

        } catch( Throwable th ) {
            EntityPropertyException.throwOnInvokeSetter( getPropertyName(), getType(), pd.getWriteMethod().getName(), param, th );
        }
    }

    /** */
    @Override
    public R invokeGetter( P pojo ) {
        try {

            Objects.requireNonNull( pojo, Tags.PRODUCT_LABEL + "Parameter 'pojo' is null");

            return (R)pd.getReadMethod().invoke( pojo );

        } catch( Throwable th ) {
            EntityPropertyException.throwOnInvokeGetter( getPropertyName(), pd.getReadMethod().toGenericString(), th );
        }
        return null; //stub
    }

    /** */
    @Override
    public boolean isRequired() {
        return isRequired;
    }

    /** */
    @Override
    public int getLength( ) {
        return length;
    }

    /** */
    @Override
    public IEntityProperty<P,?> getProxyFor() {
        return proxyFor;
    }
    public void setProxyFor( IEntityProperty<P,?> proxyFor ) {
        setProxyFor(proxyFor, null);
    }

    public void setProxyFor( IEntityProperty<P,?> proxyFor, ProxyFor annotation ) {

        if( this == proxyFor )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "ProxyFor refers to itself.");

        this.proxyFor = proxyFor;

        if( proxyFor != null ) {
            this.isTransient = true;
            this.entityColumn= null;
            this.isId        = false;

            if( annotation != null )
                this.isRequired  = !annotation.nullable();
        }
    }

    /** */
    @Override
    public String getOrderBy() {

        String retOrderBy = orderBy;

        if( retOrderBy == null )
        {
            final IEntityColumn<P,R> columnInfo = getColumnInfo( );

            if( columnInfo == null ) {

                IEntityProperty<P,?> pf = getProxyFor();
                if( pf != null )
                    retOrderBy = pf.getOrderBy();
            }
            else
                retOrderBy = columnInfo.getName();
        }//end if

        return retOrderBy;
    }

    /** */
    @Override
    public String makeOrderBy( SqlDialect dialect ) {

        String retOrderBy = orderBy;

        if( retOrderBy == null )
        {
            final IEntityColumn<P,R> columnInfo = getColumnInfo( );

            if( columnInfo == null ) {

                IEntityProperty<P,?> pf = getProxyFor();
                if( pf != null )
                    retOrderBy = pf.getOrderBy();
            }
            else
                retOrderBy = dialect.columnName( columnInfo.getName() );
        }//end if

        return retOrderBy;
    }

    /** */
    @Override
    public <T extends Annotation> T getAnnotation( Class<T> annotationClass ) {

        Method m = pd.getReadMethod( );

        if( m == null )
            m = pd.getWriteMethod( );

        return m.getAnnotation( annotationClass );
    }

    /** */
    @Override
    public boolean isReadOnly() {
        return pd.getWriteMethod() == null;
    }

    /** */
    @Override
    public RegisterEnum getCaseSensitiveSearchMode() {
        return caseSensitiveSearchMode;
    }

    /** */
    @Override
    public boolean isDecimalStripTrailingZeros() { return this.decimalStripTrailingZeros; }

    /** */
    @Override
    public boolean useInLOVPredicate() { return useInLOVPredicate;}

    /** */
    @Override
    public void dump( Map<String, Object> properties ) {

        properties.put( "name",         getName() );
        properties.put( "propertyName", getPropertyName() );
        properties.put( "id",           isId()       );
        properties.put( "transient",    isTransient());
        properties.put( "required",     isRequired() );
        properties.put( "content",      getContent() );
        properties.put( "length",       getLength()  );
        properties.put( "proxyFor",     proxyFor == null ? "-" : proxyFor.getPropertyName() );
        properties.put( "caseSensitiveSearchMode",   getCaseSensitiveSearchMode()  );
        properties.put( "decimalStripTrailingZeros", isDecimalStripTrailingZeros() );
        properties.put( "useInLOVPredicate",         useInLOVPredicate() );

        if( entityColumn != null )
        {
            Map<String, Object> columnMap = new LinkedHashMap<>();
            entityColumn.dump(columnMap);
            properties.put( "column", columnMap );
        }
    }
}
