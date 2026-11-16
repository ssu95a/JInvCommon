package ru.inversion.db;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.impl.PreparedSqlValues;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityColumn;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.dataset.parser.select.SqlSelect;

import javax.persistence.Entity;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Common lightweight configuration and mapping for one JDBC query.
 *
 * <p>The object is mutable and is not thread-safe. It never owns or closes
 * the supplied {@link TaskContext} or its connection.</p>
 */
public abstract class AbstractDBResult<
        R,
        V,
        C extends AbstractDBResult<R,V,C>
        > implements IParameters, AutoCloseable {

    public enum ModeEnum {
        QUERY,
        STORED,
        TABLE
    }

    protected final TaskContext taskContext;

    protected Class<V> valueClass;
    protected Class<R> runClass;
    protected IRowMapper<V> mapper;
    protected boolean throwOnNoDataFound;

    protected ModeEnum mode = ModeEnum.QUERY;
    protected String where;
    protected String orderBy;

    protected final ParametersValues parametersValues =
            new ParametersValues();
    protected IParameters callbackParameters;

    private String directSql;
    private String queryName;
    private PreparedSql preparedSql;

    protected AbstractDBResult(TaskContext tc) {
        taskContext = Objects.requireNonNull(tc, "tc");
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C)this;
    }

    public C valueClass(Class<V> valueClass) {
        this.valueClass = Objects.requireNonNull(
                valueClass, "valueClass");
        invalidateSql();
        return self();
    }

    public C runClass(Class<R> runClass) {
        this.runClass = Objects.requireNonNull(runClass, "runClass");
        invalidateSql();
        return self();
    }

    public C mode(ModeEnum mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        invalidateSql();
        return self();
    }

    /** Use the table mapped by {@code valueClass}. */
    public C table() {
        return mode(ModeEnum.TABLE);
    }

    /** Use a named native query declared on runClass or valueClass. */
    public C queryName(String queryName) {
        this.queryName = optionalText(queryName);
        directSql = null;
        mode = ModeEnum.QUERY;
        invalidateSql();
        return self();
    }

    /** Use the supplied SELECT text before optional transformations. */
    public C sql(String sql) {
        directSql = optionalText(sql);
        queryName = null;
        mode = ModeEnum.QUERY;
        invalidateSql();
        return self();
    }

    public C mapper(IRowMapper<V> mapper) {
        this.mapper = mapper;
        return self();
    }

    public C throwOnNoDataFound(boolean doThrow) {
        throwOnNoDataFound = doThrow;
        return self();
    }

    /**
     * Set a fallback parameter source. Values set directly on this object
     * take precedence over this source.
     */
    public C parameters(IParameters parameters) {
        callbackParameters = parameters;
        return self();
    }

    /** Set a predicate on the outer SELECT. */
    public C where(String where) {
        this.where = optionalText(where);
        invalidateSql();
        return self();
    }

    /** Replace or add ORDER BY on the outer SELECT. */
    public C orderBy(String orderBy) {
        this.orderBy = optionalText(orderBy);
        invalidateSql();
        return self();
    }

    @Override
    public C set(String parameterName, Object value) {
        setParameter(parameterName, value);
        return self();
    }

    /** Positional runtime parameters are zero-based. */
    @Override
    public C set(int parameterIndex, Object value) {
        setParameter(parameterIndex, value);
        return self();
    }

    @Override
    public void setParameter(String parameterName, Object value) {
        parametersValues.setParameter(
                Objects.requireNonNull(parameterName, "parameterName"),
                value
        );
    }

    /** Positional runtime parameters are zero-based. */
    @Override
    public void setParameter(int parameterIndex, Object value) {
        if( parameterIndex < 0 )
            throw new IllegalArgumentException(
                    "parameterIndex must be >= 0");

        parametersValues.setParameter(parameterIndex, value);
    }

    @Override
    public boolean hasParameter(String parameterName) {
        return parametersValues.hasParameter(parameterName)
                || callbackParameters != null
                && callbackParameters.hasParameter(parameterName);
    }

    @Override
    public boolean hasParameter(int parameterIndex) {
        return parametersValues.hasParameter(parameterIndex)
                || callbackParameters != null
                && callbackParameters.hasParameter(parameterIndex);
    }

    @Override
    public Object getParameter(String parameterName) {
        if( parametersValues.hasParameter(parameterName) ) {
            Object value = parametersValues.getParameter(parameterName);
            if( value != NO_PARAMETER_VALUE )
                return value;
        }

        if( callbackParameters != null
                && callbackParameters.hasParameter(parameterName) ) {
            Object value = callbackParameters.getParameter(parameterName);
            if( value != NO_PARAMETER_VALUE )
                return value;
        }

        throw missingParameter(parameterName);
    }

    @Override
    public Object getParameter(int parameterIndex) {
        if( parametersValues.hasParameter(parameterIndex) ) {
            Object value = parametersValues.getParameter(parameterIndex);
            if( value != NO_PARAMETER_VALUE )
                return value;
        }

        if( callbackParameters != null
                && callbackParameters.hasParameter(parameterIndex) ) {
            Object value = callbackParameters.getParameter(parameterIndex);
            if( value != NO_PARAMETER_VALUE )
                return value;
        }

        throw missingParameter(parameterIndex);
    }

    /** Build and cache the immutable SQL/parameter plan. */
    protected final PreparedSql preparedSql() {
        if( preparedSql != null )
            return preparedSql;

        String source;

        switch( mode ) {
            case QUERY:
                source = querySql();
                break;
            case TABLE:
                source = tableSql();
                break;
            case STORED:
                throw new UnsupportedOperationException(
                        "STORED mode has no defined result contract");
            default:
                throw new IllegalStateException("Unknown mode: " + mode);
        }

        SqlSelect select = new SqlSelect(source, taskContext.dialect());

        if( where != null )
            select.setPredicates(Collections.singletonList(where));

        if( orderBy != null )
            select.setOrderBy(orderBy);

        preparedSql = select.prepare();
        return preparedSql;
    }

    /** Resolve and convert values in physical JDBC placeholder order. */
    protected final List<Object> jdbcValues(PreparedSql sql) {
        return PreparedSqlValues.jdbc(sql, this);
    }

    protected final void bind(
            PreparedStatement statement,
            List<?> values
    ) throws SQLException {
        int index = 1;
        for( Object value : values )
            statement.setObject(index++, value);
    }

    /** Resolve the row mapper once per execution. */
    @SuppressWarnings("unchecked")
    protected final IRowMapper<V> createRowMapper(ResultSet rs)
            throws Exception {
        if( mapper != null )
            return mapper;

        IRowMapper<V> result = mapperFrom(runClass);
        if( result == null )
            result = mapperFrom(valueClass);
        if( result == null && this instanceof IRowMapper )
            result = (IRowMapper<V>)this;

        if( result != null )
            return result;

        if( valueClass != null
                && (mode == ModeEnum.TABLE
                || valueClass.isAnnotationPresent(Entity.class)) ) {
            return EntityMetadataFactory
                    .getEntityMetaData(valueClass)
                    .createRSRowMapper(rs);
        }

        return scalarMapper(rs);
    }

    protected final JInvDbException executionFailure(
            Throwable failure,
            PreparedSql sql,
            List<?> values
    ) {
        if( failure instanceof Error )
            throw (Error)failure;

        if( failure instanceof JInvDbException )
            return (JInvDbException)failure;

        return new JInvDbException(
                failure,
                sql == null ? null : sql.getSql(),
                values
        );
    }

    private String querySql() {
        if( directSql != null )
            return directSql;

        String result = nativeQuery(runClass);
        if( result == null )
            result = nativeQuery(valueClass);

        if( S.isNullOrEmpty(result) ) {
            String target = runClass != null
                    ? runClass.getName()
                    : valueClass == null ? "<unspecified>"
                    : valueClass.getName();

            throw new IllegalStateException(
                    Tags.PRODUCT_LABEL
                    + "Native query is not defined for " + target
                    + (queryName == null ? "" : ", name=" + queryName));
        }

        return result;
    }

    private String nativeQuery(Class<?> sourceClass) {
        return sourceClass == null
                ? null
                : XXIDsDao.getNativeQuery(
                        taskContext, sourceClass, queryName);
    }

    private String tableSql() {
        if( valueClass == null )
            throw new IllegalStateException(
                    Tags.PRODUCT_LABEL
                    + "valueClass is required for TABLE mode");

        IEntityMetaData<V> metadata =
                EntityMetadataFactory.getEntityMetaData(valueClass);
        SqlDialect dialect = taskContext.dialect();
        String tableName = metadata.getTableName();

        if( S.isNullOrEmpty(tableName) )
            throw new IllegalStateException(
                    Tags.PRODUCT_LABEL
                    + "Entity table is not defined for "
                    + valueClass.getName());

        List<String> columns = new ArrayList<>();

        for( Map.Entry<String,IEntityProperty<V,?>> entry
                : metadata.getColumnsMap().entrySet() ) {
            IEntityProperty<V,?> property = entry.getValue();
            IEntityColumn<V,?> column = property.getColumnInfo();

            if( column == null || column.getQueryExpr() != null )
                continue;

            String columnTable = column.getTableName();
            if( S.isNotNullOrEmpty(columnTable)
                    && !tableName.equalsIgnoreCase(columnTable) )
                continue;

            columns.add(dialect.columnName(property));
        }

        if( columns.isEmpty() )
            throw new IllegalStateException(
                    Tags.PRODUCT_LABEL
                    + "No persistent columns found for "
                    + valueClass.getName());

        return "SELECT " + String.join(",", columns)
                + " FROM " + dialect.tableName(tableName);
    }

    @SuppressWarnings("unchecked")
    private IRowMapper<V> mapperFrom(Class<?> candidate)
            throws Exception {
        if( candidate == null
                || !IRowMapper.class.isAssignableFrom(candidate) )
            return null;

        Constructor<?> constructor = candidate.getDeclaredConstructor();
        if( !constructor.isAccessible() )
            constructor.setAccessible(true);

        return (IRowMapper<V>)constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    private IRowMapper<V> scalarMapper(ResultSet rs)
            throws SQLException {
        final int columnCount = rs.getMetaData().getColumnCount();
        final Class<V> targetClass = valueClass;

        return (rowSet, rowNumber) -> {
            if( targetClass == null ) {
                if( columnCount == 1 )
                    return (V)rowSet.getObject(1);

                return (V)objectArray(rowSet, columnCount);
            }

            if( targetClass.isArray() ) {
                if( targetClass == Object[].class )
                    return (V)objectArray(rowSet, columnCount);

                Class<?> itemClass = targetClass.getComponentType();
                Object values = Array.newInstance(itemClass, columnCount);

                for( int i = 1; i <= columnCount; i++ )
                    Array.set(
                            values,
                            i - 1,
                            TypeConverter.convert(
                                    rowSet.getObject(i), itemClass)
                    );

                return (V)values;
            }

            Object value = rowSet.getObject(1);
            return targetClass == Object.class
                    ? (V)value
                    : TypeConverter.convert(value, targetClass);
        };
    }

    private static Object[] objectArray(
            ResultSet rs,
            int columnCount
    ) throws SQLException {
        Object[] values = new Object[columnCount];
        for( int i = 1; i <= columnCount; i++ )
            values[i - 1] = rs.getObject(i);
        return values;
    }

    private void invalidateSql() {
        preparedSql = null;
    }

    private static String optionalText(String value) {
        if( value == null )
            return null;

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static IllegalStateException missingParameter(Object parameter) {
        return new IllegalStateException(
                Tags.PRODUCT_LABEL
                + "No value for parameter '" + parameter + "'");
    }

    @Override
    public void close() {
        // DBSingleResult has no live resources after execute().
    }
}
