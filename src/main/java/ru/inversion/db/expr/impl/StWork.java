package ru.inversion.db.expr.impl;
import org.slf4j.Logger;
import ru.inversion.db.JInvDbException;
import ru.inversion.db.expr.SQLExpressionException;
import ru.inversion.db.expr.SQLParameterException;
import ru.inversion.db.expr.SQLParameterNotSetException;
import ru.inversion.db.work.DBParametersWork;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.utils.AutoCloseableList;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import javax.persistence.ParameterMode;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

import static javax.persistence.ParameterMode.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 *
 * @author ssu
 */
public class StWork implements DBParametersWork {
    private final static Logger logger = getLogger( MethodHandles.lookup().lookupClass() );

    final private String                           preparedSqlText;
    final private CallableStatement                statement;

    final private Map  <String, ParameterMetadata> metaParameterNamedMap;
    final private List <ParameterMetadata>         metaParameterIndexList;

    final private List <Object>                    indexParametersValue = new ArrayList<>();
    final private Map  <String, Object>            namedParametersValue = new HashMap<>();

    private boolean doCloseStatement = true;

    /** */
    public StWork (
        String sqlText,
        Connection connection,
        Map  <String, ParameterMetadata> mp,
        List <ParameterMetadata> ml
    ) throws SQLExpressionException
    {
        Objects.requireNonNull( sqlText, "sqlText" );

        if( connection != null )
        {
            this.preparedSqlText = sqlText;

            try {
                this.statement = connection.prepareCall( preparedSqlText );
                this.statement.closeOnCompletion();

            } catch (SQLException ex) {
                SQLExpressionException.throwOnPrepareJDBCCallable( preparedSqlText, ex );
                throw new SQLExpressionException(); //stub
            }
        }
        else
        {
            this.preparedSqlText = sqlText;
            this.statement       = null;
        }

        this.metaParameterNamedMap = U.nvl( mp, Collections.<String,ParameterMetadata>emptyMap() );
        this.metaParameterIndexList= U.nvl( ml, Collections.<ParameterMetadata>emptyList() );

        if( mp != null )
        {
            for( Map.Entry<String, ParameterMetadata> e : metaParameterNamedMap.entrySet() )
            {
                 if( e.getValue().isDefInParameterList() )
                 {
                    if( statement != null )
                        configureStatementParameter( statement, e.getValue() );

                    namedParametersValue.put( e.getValue().getName(), e.getValue().getDefaultValue() );
                 }
            }//end if
        }

        if( ml != null )
        {
            for( ParameterMetadata p : metaParameterIndexList )
            {
                if( true /*p.isDefInParameterList()*/ )
                {
                    if( statement != null )
                        configureStatementParameter( statement, p );

                    indexParametersValue.add( p.getDefaultValue() );
                }//end if
            }
        }
    }

    /** */
    private boolean isOwnerStatement() { return statement != null; }

    /** */
    private void configureStatementParameter( CallableStatement cs, ParameterMetadata pm ) throws SQLParameterException {

        if( U.in( pm.getMode(), ParameterMode.IN, ParameterMode.INOUT ) )
        {
            List<Integer> jdbcStatementIndexes = pm.getJDBCStatementIndexes();

            for( Integer idx : jdbcStatementIndexes )
            {
                try {
                    cs.setObject( idx, null );
                } catch( SQLException ex ) {
                    if( pm.isIndexed() ) SQLParameterException.onSet( pm.getPosition(), null, ex ); else SQLParameterException.onSet( pm.getName(), null, ex );
                }
            }//end for

        }//end if

        if( U.in( pm.getMode(), ParameterMode.OUT, ParameterMode.INOUT ) )
        {
            try {

                List<Integer> jdbcStatementIndexes = pm.getJDBCStatementIndexes();

                for( Integer idx : jdbcStatementIndexes )
                {
                    if( pm.getElementType() != null )
                        cs.registerOutParameter( idx, type2code(pm.getSQLType()), pm.getElementType() );
                    else
                        cs.registerOutParameter( idx, type2code(pm.getSQLType()) );
                }
            } catch( Throwable th ) {
                if( pm.isIndexed() ) SQLParameterException.onSet( pm.getPosition(), null, th ); else SQLParameterException.onSet( pm.getName(), null, th );
            }
        }//end if
    }

    /** */
    @Override
    public void setParameter( String parameterName, Object value ) {
        ParameterMetadata p = metaParameterNamedMap.get( parameterName );
        if( p != null )
            namedParametersValue.put( parameterName, value );
    }

    /** */
    @Override
    public void setParameter( int parameterIndex, Object value ) {

        if( metaParameterIndexList.size() <= parameterIndex )
            ; //throw new RuntimeException();
        else
        {
            ParameterMetadata p = metaParameterIndexList.get(parameterIndex);
            if( p != null )
                indexParametersValue.set( parameterIndex, value );
        }
    }

    /** */
    private void checkParametersBeforeExcecute( ) throws SQLParameterNotSetException {

//        metaParameterNamedMap.entrySet().stream().forEach (
//            (e)-> {
//                if(
//                    U.in( e.getValue().getMode(), IN, INOUT )
//                 && !e.getValue().isDefInParameterList()
//                 && !namedParametersValue.containsKey(e.getKey())
//                )
//                    throw new SQLParameterNotSetException(e.getKey());
//            }
//        );

        for( Map.Entry<String, ParameterMetadata> e : metaParameterNamedMap.entrySet() ) {
            if (
                U.in( e.getValue().getMode(), IN, INOUT )
             && !e.getValue().isDefInParameterList()
             && !namedParametersValue.containsKey(e.getKey())
            )
                throw new SQLParameterNotSetException( e.getKey() );
        }

        for( ParameterMetadata p : metaParameterIndexList )
        {
            if( U.in( p.getMode(), IN, INOUT ) && !p.isDefInParameterList() )
            {
                if( p.getPosition() >= indexParametersValue.size() || indexParametersValue.get( p.getPosition() ) == null )
                    throw new SQLParameterNotSetException( p.getPosition() );
            }
        }
    }

    /** */
    private int type2code( Integer t ) {
        if( t == Types.REF_CURSOR )
            return -10;
        return t;
    }

    /** */
    private Object getArrayValue1( Connection connection, ParameterMetadata pm, Collection col ) throws SQLParameterException {

        try {

            StringBuilder sb = new StringBuilder("begin ? := ");

            if( col == null || col.isEmpty() )
                sb.append( pm.getElementType() ).append("()");
            else {

                sb.append( pm.getElementType() ).append("( ");
                for( int i = col.size(); i > 0; i-- )
                    sb.append("?,");

                sb.setCharAt( sb.length() - 1, ')' );
            }
            sb.append("; end;");

            try ( final CallableStatement cs = connection.prepareCall( sb.toString() ) ) {

                cs.registerOutParameter( 1, Types.ARRAY, pm.getElementType() );

                int i = 2;

                for( Object o : col )
                     cs.setObject( i++, o );

                cs.execute ();

                return cs.getArray (1);
            }
        }
        catch( Throwable th ) {
            throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error getting a parameter of type ARRAY from DB", th );
        }
    }

    private static Method createARRAY;

    /** */
    private Object getArrayValue( Connection connection, ParameterMetadata pm, Collection col ) throws SQLParameterException {

        try {

            if( createARRAY == null ) {
                createARRAY = connection.getClass().getMethod( "createARRAY", String.class, Object.class );
                createARRAY.setAccessible(true);
            }

            return createARRAY.invoke( connection, pm.getElementType(), col.toArray() );

//            ISupportArray sa = (ISupportArray)connection;
//            return sa.createARRAY( preparedSqlText, pm.getElementType() );
        }
        catch( Throwable th ) {
            throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error getting a parameter of type ARRAY from DB", th );
        }
    }

    /** */
    private void setParameterValue( CallableStatement cs, ParameterMetadata pm ) throws SQLParameterException {

        Object originalValue = pm.isIndexed() ? getParameter( pm.getPosition() ) : getParameter( pm.getName() );
        Object handleValue   = originalValue;

        try( AutoCloseableList acList = new AutoCloseableList() ) {

            if( originalValue != null ) {

                if( pm.getSQLType() != null && pm.getSQLType() == Types.ARRAY ) {

                    try {
                        List list = (List)originalValue;
                        handleValue = getArrayValue( cs.getConnection(), pm, list );
                    }
                    catch( ClassCastException e ) {
                        throw new SQLParameterException("Для параметра типа ARRAY значение должно быть List.", e );
                    }
                }

                if( originalValue instanceof File ) {
                    FileInputStream is = new FileInputStream( (File)originalValue );
                    acList.add(is);
                    handleValue = is;
                }

//                if( originalValue instanceof Blob )
//                    handleValue = ((Blob)originalValue).getBinaryStream();
            }

            final Object v = Jt2St.convert(handleValue);

            for( int idx : pm.getJDBCStatementIndexes() ) {

                try {

                    if( v instanceof InputStream)
                    {
                        InputStream is = (InputStream)v;
                        cs.setBinaryStream( idx, is );
                    }
                    else if( v instanceof Blob )
                    {
                        Blob b = (Blob)v;
                        cs.setBlob(idx, b );
                    }
                    else if( v instanceof Reader)
                    {
                        Reader r = (Reader)v;
                        cs.setCharacterStream( idx, r );
                    }
                    else
                    {
                        Object v1 = Jt2St.convert(v);

                        if( pm.getSQLType() == null /*|| pm.getSQLType().getVendorTypeNumber() == Types.ARRAY*/ )
                            cs.setObject( idx, v1 );
                        else
                            cs.setObject( idx, v1, type2code( pm.getSQLType() ) );
                    }

                } catch( Throwable th ) {
                    SQLParameterException.onSetInStatement( idx, v, th );
                }//end if
            }
        }
        catch( Throwable th ) {
            if( pm.isIndexed() )
                SQLParameterException.onSet( pm.getPosition(), originalValue, th );
            else
                SQLParameterException.onSet( pm.getName(), originalValue, th );
        }
    }

    /** */
    protected Object getParameterValue( CallableStatement cs, ParameterMetadata pm ) throws SQLParameterException {

        try {

            if( pm.getMode() == IN )
                throw new IllegalStateException("No 'getValue' for parameter in 'IN' mode.");

            int parameterIndex = pm.getJDBCStatementIndexes().get(0);

            Object value = null;

            if( pm.getSQLType() == Types.ARRAY )
                value = cs.getArray ( parameterIndex );
            else
                value = cs.getObject( parameterIndex );

            if( value != null )
            {
                if( value instanceof Array )
                {
                    Array a = (Array)value;

                    List<Object> list = Arrays.asList( (Object[])a.getArray() );

                    // конвертируем содержимое в JavaType, если задан
                    if( list != null && pm.getParameterType() != null ) {

                        for( int i = 0; i < list.size(); i++ )
                             list.set( i, TypeConverter.convert( list.get(i), pm.getParameterType() ) );
                    }

                    value = list;
                }
                else
                {
                    if( pm.getParameterType() != null )
                        value = TypeConverter.convert( value, pm.getParameterType() );
                    else
                        value = Jt2St.r_convert( value );
                }
            }
            return value;

        } catch( Throwable th ) {
            if( pm.isIndexed() ) SQLParameterException.onGet( pm.getPosition(), th ); else SQLParameterException.onGet( pm.getName(), th );
        }

        return null;
    }

    /** */
    @Override
    public void execute( Connection connection ) throws SQLException {

        try {

            checkParametersBeforeExcecute( );

            CallableStatement cs = null;

            try {

                if( isOwnerStatement() ) {
                    cs = statement;
                }
                else
                {
                    try {
                        cs = connection.prepareCall( preparedSqlText );
                        cs.closeOnCompletion();
                    }
                    catch( SQLException ex ) {
                        SQLExpressionException.throwOnPrepareJDBCCallable( preparedSqlText, ex );
                    }

                    // no lambda, because throws
                    for( Map.Entry<String, ParameterMetadata> e : metaParameterNamedMap.entrySet() )
                        configureStatementParameter( cs, e.getValue() );

                    for( ParameterMetadata p : metaParameterIndexList )
                        configureStatementParameter( cs, p );
                }

                for( Map.Entry<String, ParameterMetadata> e : metaParameterNamedMap.entrySet() ) {
                    if( U.in( e.getValue().getMode(), IN, INOUT ) )
                        setParameterValue( cs, e.getValue() );
                }//end if

                // metaParameterIndexList.stream().filter( (e)->e.getMode() == IN ).forEach( (p)-> setParameterValue( cs2, p ) );
                for( ParameterMetadata p : metaParameterIndexList ) {
                    if( U.in( p.getMode(), IN, INOUT ) )
                        setParameterValue( cs, p );
                }

                try {
                    int rowCount = cs.executeUpdate();
                }
                catch( SQLException ex ) {
                    throw new JInvDbException( ex, preparedSqlText );
                }

                for( Map.Entry<String, ParameterMetadata> entry : metaParameterNamedMap.entrySet() )
                {
                    if( U.in( entry.getValue().getMode(), INOUT, OUT ) ) {

                        Object v = getParameterValue( cs, entry.getValue() );

                        if( doCloseStatement && v != null )
                            doCloseStatement = !(v instanceof ResultSet);

                        namedParametersValue.put( entry.getKey(), v );
                    }
                }

                for( ParameterMetadata p : metaParameterIndexList )
                {
                    if( U.in( p.getMode(), INOUT, OUT ) ) {

                        Object v = getParameterValue( cs, p );

                        if( doCloseStatement && v != null )
                            doCloseStatement = !(v instanceof ResultSet);

                        indexParametersValue.set( p.getPosition(), v );
                    }
                }
            }
            finally {
                if( cs != null ) {
                    if( !isOwnerStatement() )
                        if( doCloseStatement )
                             cs.close();
                }
            }
        }
        catch( Throwable th ) {
            throw new SQLException( "execute", th );
        }
    }

    /** */
    @Override
    public Object getParameter( String parameterName ) {
        return namedParametersValue.get( parameterName );
    }

    /** */
    @Override
    public Object getParameter( int parameterIndex ) {
        return parameterIndex >= indexParametersValue.size() ? null : indexParametersValue.get(parameterIndex);
    }

    /** */
    @Override
    public void getParametersMap( Map<String, Object> valueMap, boolean onlyDef ) {

        if( namedParametersValue == null || valueMap == null )
            return;

        if( onlyDef )
            namedParametersValue.forEach( (String t, Object u) -> { if( valueMap.containsKey(t) ) valueMap.put( t, u ); });
        else
            valueMap.putAll(namedParametersValue);
    }

    /** */
    @Override
    public void getParametersList( List<Object> valueList ) {

        if( valueList == null || indexParametersValue.isEmpty() )
            return;

        for( int i = 0; i < indexParametersValue.size(); i++ )
        {
            if( i < valueList.size() )
                valueList.set( i, indexParametersValue.get(i) );
            else
                break;

        }//end for
    }

    /** */
    @Override
    public List<Object> getParametersList( ) {
        return indexParametersValue;
    }

    /** */
    @Override
    public void close() throws Exception {
        if( isOwnerStatement() && doCloseStatement )
            statement.close();
    }

    /** */
    @Override
    public void setParameterBean( Object bean ) {

        if( bean == null )
            return;

        try {

            final Map< String, IEntityProperty< Object, ? > > propertyDescriptorsMap =
                    EntityMetadataFactory.getEntityMetaData( (Class<Object>)bean.getClass()).getPropertiesMap();

            for( Map.Entry< String, IEntityProperty< Object, ? > > entry : propertyDescriptorsMap.entrySet() )
            {
                Object value = null;
                try {
                    value = entry.getValue().invokeGetter(bean);
                } catch( Throwable ex) {
                    SQLParameterException.onSet( entry.getKey(), null, ex );
                }
                setParameter( entry.getKey(), value );
            }
        }
        catch( Throwable th ) {
            throw new RuntimeException( "Ошибка при установке параметров из bean класса " + bean.getClass().getName(), th);
        }
    }
}
