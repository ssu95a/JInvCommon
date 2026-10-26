package ru.inversion.tds;

import ru.inversion.dataset.*;
import ru.inversion.dataset.impl.JDBCDataReader;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.db.JInvDbException;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.select.SqlSelect;

import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ru.inversion.dataset.DataSetEvent.DataSetEventType.EXECUTE;
import static ru.inversion.dataset.DataSetEvent.DataSetEventType.FIRST_TIME_EXECUTE;
import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.INSERT;
import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.REFRESH;
import static ru.inversion.dataset.SQLDataSet.*;
import static ru.inversion.meta.EntityMetadataFactory.getEntityMetaData;
import static ru.inversion.tds.SQLTreeDataSet.PredicateTypeEnum.*;

/** */
public class SQLTreeDataSet<P> extends AbstractTreeDataSet<P> {

    /** Константы откуда брать данные для запроса */

    /** по аннотации @Table */
    public final static String QUERY_TABLE   ="$TABLE$";

    /** по аннотации @NamedNativeQuery */
    public final static String QUERY_UNNAMED ="$UNNAMED$";

    /**
     * Генератор идентификаторов для фильтров.
     */
    final static private AtomicInteger g_filterIDSequence = new AtomicInteger(0);


    public enum PredicateTypeEnum {

        /** */
        WHERE_PREDICATE,

        /**
         * Временный
         */
        TEMPORARY_FILTER,

        /**
         * Постоянный
         */
        FIXED_FILTER,

        /**
         * Автофильтр - устанавливается автоматически перед использованием
         */
        AUTO_FILTER
    };

    /*
     * Одно выражение для установленного в DataSet фильтра.
     * <p>
     * Фильтр DataSet - часть предиката (where) SQL выражения на котором построен DataSet
     *
     * Для внутреннего использования.
     */

    /** */
    protected static class PredicateItem {

        private final   String filterStr;
        private final   PredicateTypeEnum type;
        private boolean pending;

        /** */
        public PredicateItem( String filterStr, PredicateTypeEnum type )
        {
            this.filterStr = Objects.requireNonNull( filterStr, "'filterStr' is null" );
            this.type      = Objects.requireNonNull( type, "'type' is null" );
            this.pending   = true;
        }

        /** */
        public PredicateTypeEnum getType() {
            return type;
        }

        /** */
        public boolean isPending() {
            return pending;
        }

        /** */
        public boolean isFinal() {
            return !pending;
        }

        /** */
        public void makeFinal() {
            this.pending = false;
        }

        /** */
        public boolean isTemporary() {
            return type == PredicateTypeEnum.TEMPORARY_FILTER;
        }

        /** */
        public boolean isFixed() {
            return type == PredicateTypeEnum.FIXED_FILTER;
        }

        /** */
        public boolean isWherePredicate() {
            return type == PredicateTypeEnum.WHERE_PREDICATE;
        }

        /** */
        public boolean isAutoFilter() {
            return type == PredicateTypeEnum.AUTO_FILTER;
        }

        /** */
        public String getFilterStr() {
            return filterStr;
        }

        /** */
        public boolean isFilter() {
            return type == PredicateTypeEnum.FIXED_FILTER || type == PredicateTypeEnum.TEMPORARY_FILTER;
        }
    }

    /** */
    protected List<Comparable<?>> rootIdValues = ROOT_NULL_VALUE;

    /**
     * Коллекция доп фильтров усиливающая предикат.
     */
    protected Map<Integer, PredicateItem> predicates = new TreeMap<>();


    /**
     * TaskContext, обеспечивает доступ к соединению с БД.
     */
    private TaskContext taskContext;


    /** Выражение отвечающее за сортировку в окончательном SQL, на котором строиться выборка. */
    private  String  orderByStmnt;

    /**
     * Query alias.
     */
    protected String queryAlias;

    /**
     * Имя запроса, если DataSet строится на аннотации NamedNativeQueries
     */
    protected String nativeQueryName = QUERY_TABLE;

    /**
     * Маппер для загрузки записей из JDBC ResultSet.
     */
    private IRowMapper<P> rowMapper;

    /** */
    public SQLTreeDataSet( ) {
        super();
    }

    /** */
    public SQLTreeDataSet( Class<? super P> rowClass ) {
        super(rowClass);
    }

    /** */
    public SQLTreeDataSet( TaskContext tc, Class<? super P> rowClass ) {
        super(rowClass);
        this.taskContext = tc;
    }

    /** */
    public void setRootIdValues(List<Comparable<?>> rootIdValues)
    {
        if( rootIdValues == null )
        {
            this.rootIdValues = ROOT_NULL_VALUE;
            return;
        }

        this.rootIdValues = Collections.unmodifiableList( new ArrayList<>(rootIdValues) );
    }

    /** */
    public List<Comparable<?>> getRootIdValues(  )
    {
        return this.rootIdValues;
    }

    /** */
    public SQLTreeDataSet<P> rootIdValues( List<Comparable<?>> riv )
    {
        setRootIdValues(riv);
        return this;
    }

    /**
     * Установка Rel-OO маппера для SQLDataSet.
     * <p>
     * После установки сбрасывает флаг состояния SQL выражения.
     * @param rowMapper
     *
     * @see IRowMapper
     * @see #getRowMapper()
     */
    public void setRowMapper( IRowMapper<P> rowMapper ) {
        this.rowMapper = rowMapper;
    }

    /**
     * Возвращает Rel-OO маппер установленный для SQLDataSet.
     * <p>
     * @return маппер.
     *
     * @see IRowMapper
     * @see #getRowMapper()
     */
    public IRowMapper<P> getRowMapper( ) {
        return rowMapper;
    }


    /**
     * Устанавливает TaskContext для доступа к БД.
     * @param taskContext
     */
    public void setTaskContext(TaskContext taskContext ) {
        this.taskContext = taskContext;
    }

    /**
     * Возвращает TaskContext установленный методом {@link #setTaskContext(TaskContext) }.
     *
     * @return taskContext
     * @see TaskContext
     */
    public TaskContext getTaskContext( ) {
        return this.taskContext;
    }
    public SQLTreeDataSet<P> taskContext( TaskContext taskContext ) {
        setTaskContext( taskContext );
        return this;
    }

    /** */
    protected TaskContext getTaskContextForUse( ) {

        TaskContext tc = getTaskContext( );

        if( tc == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "SQLTreeDataSet: TaskContext is null" );

        return tc;
    }

    /**
     * Возвращает Query alias для запроса, установленного методом setQueryAlias.
     * @return  queryAlias
     * @see #queryAlias
     * @see #setQueryAlias(java.lang.String)
     */
    public String getQueryAlias( ) {
        return queryAlias;
    }

    public void setQueryAlias( String qa ) {
        queryAlias = qa;
    }

    /** */
    public void setNativeQueryName( String nqn ) {

        String newNativeQueryName = ( nqn == null ? null : nqn.trim() );

        if( S.isNullOrEmpty( newNativeQueryName )  )
            newNativeQueryName = QUERY_UNNAMED;

        if( !Objects.equals( newNativeQueryName, this.nativeQueryName ) )
        {
            this.nativeQueryName = newNativeQueryName;
        }
    }

    /** */
    public String getNativeQueryName( ) {
        return this.nativeQueryName;
    }

    /** */
    public SQLTreeDataSet<P> nativeQueryName( String nqn ) {
        setNativeQueryName(nqn);
        return this;
    }

    /**
     * Возвращает SQL строку из аннотации NamedNativeQuery над классом Pojo.
     * <p>
     * @see NamedNativeQuery
     */
    private String getNativeQuery( ) {

        final Class<? extends P>  clazz = getRowClass();

        return XXIDsDao.getNativeQuery( getTaskContext(), clazz, QUERY_UNNAMED.equals(this.nativeQueryName) ? null : this.nativeQueryName );
    }

    /**
     * Получение SQL на которой строится выражение получения данных.
     */
    private String internalGetSQL( ) {

        Class<? extends P> clazz = getRowClass();

        if( clazz == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No RowClass set" );

        String sql = null;

        if( QUERY_TABLE.equals(this.nativeQueryName) )
        {
            Table table = clazz.getAnnotation(Table.class);

            if( table != null && !S.isNullOrEmpty(table.name()) )
            {
                sql = Arrays.stream(
                         clazz.getMethods() )
                        .filter(methodFilter)
                        .map((m) -> columnMapper.apply(m, null,getTaskContextForUse().dialect()))
                        .collect(Collectors.joining(",", "SELECT ", " FROM " + table.name()));
            }//end if
            else
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "The @Table annotation is not set" );
        }
        else
            sql = getNativeQuery();

        return sql;
    }

    /** {@inheritDoc} */
    public String getOrderBy( )  {
        return orderByStmnt;
    }

    /** */
    public void setOrderBy( String s ) { orderByStmnt = s; }

    /**
     *  Получить полное выражение OrderBy
     */
    public String getCompletedOrderBy( ) {

        String orderBy = getOrderBy();

        if( S.isNullOrEmpty(orderBy) )
            return S.EMPTY_STRING;

        if( orderBy.indexOf('~') == -1 )
            return orderBy;

        boolean insideStr = false;
        char    ch;

        StringBuilder sb = new StringBuilder( orderBy.length() );

        for( int i = 0; i < orderBy.length(); i++ ) {

            ch = orderBy.charAt(i);

            if( ch == "'".charAt(0) )
                insideStr = !insideStr;

            if( ch == '~' && !insideStr )
                continue;

            sb.append( ch );
        }

        return sb.toString();
    }

    /** */
    public int setWherePredicate( String wherePredicate, boolean append )
    {
        if (!append)
            removePredicates(PredicateItem::isWherePredicate);

        return addPredicate(wherePredicate, WHERE_PREDICATE);
    }

    /** {@inheritDoc} */
    public void setWherePredicate( String wherePredicate ) {
        setWherePredicate(wherePredicate, false );
    }

    /** {@inheritDoc} */
    public String removeWherePredicate( int predicateId ) {
        PredicateItem item = this.predicates.remove(predicateId);
        return item == null ? null : item.getFilterStr();
    }

    /** */
    private int addPredicate( String predicate, PredicateTypeEnum type )
    {
        String text = predicate == null ? null : predicate.trim();
        if( S.isNullOrEmpty(text) )
            return -1;

        int id = g_filterIDSequence.incrementAndGet();
        predicates.put( id, new PredicateItem(text, type) );
        return id;
    }


    /** */
    private void appendPredicates(StringBuilder sb) {

        if( predicates.isEmpty() )
            return;

        sb.append("\nwhere null is null");

        predicates.values().forEach(
            predicate -> sb.append(" AND ").append(predicate.getFilterStr())
        );
    }

    /**
     * Версия метода setWherePredicate(String) в стиле "Fluent interface".
     * <p>
     * @return
     *          this
     * @see #setWherePredicate(String)
     */
    public SQLTreeDataSet<P> wherePredicate( String wherePredicate ) {
        setWherePredicate(wherePredicate);
        return this;
    }

    /** */
    private void removePredicates( Predicate<PredicateItem> predicate )
    {
        if (predicate == null || predicates.isEmpty())
            return;
        predicates.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    }

    /** */
    public int setFilter( String filterString, boolean fixed, boolean append )
    {
        PredicateTypeEnum type = fixed ? FIXED_FILTER : TEMPORARY_FILTER;

        if( !append )
        {
            if (fixed) {
                removePredicates(PredicateItem::isFixed);
            } else {
                removePredicates(PredicateItem::isTemporary);
            }
        }

        return addPredicate(filterString, type);
    }

    /** {@inheritDoc} */
    public String removeFilter( int filterId ) throws TreeDataSetException {
        PredicateItem item = predicates.remove(filterId);
        return item == null ? null : item.getFilterStr();
    }

    /**
     * Версия метода setFilter в стиле "Fluent interface".
     * <p>
     * @return
     *          this
     *
     * @see #setFilter(java.lang.String, boolean, boolean)
     */
    public SQLTreeDataSet<P> filter( String filterString, boolean fixed, boolean append ) throws TreeDataSetException {
        setFilter( filterString, fixed, append );
        return this;
    }

    /** */
    public void clearFilters( boolean allFilters ) throws TreeDataSetException {

        if( allFilters )
            predicates.entrySet().removeIf( (t) -> t.getValue().isFilter() );
        else
            predicates.entrySet().removeIf( (t) -> t.getValue().isTemporary() );
    }

    /**
     * Готовит SQL выражение для обычной загрузки дерева.
     */
    protected String prepareCompleteSql() throws TreeDataSetException {
        return prepareCompleteSql(false);
    }

    /**
     * Готовит SQL выражение для обновления одной строки дерева.
     */
    protected String prepareRowDataSql() throws TreeDataSetException {
        return prepareCompleteSql(true);
    }

    private String prepareCompleteSql( boolean rowData ) throws TreeDataSetException {

        StringBuilder sb = new StringBuilder();

        final String sql = internalGetSQL();
              String mrk = getMarkSQLPart();

        if( S.isNullOrEmpty(mrk) && isSupportMark() )
            mrk = "0 MARK";

        final String qa = ( mrk == null && QUERY_TABLE.equals(nativeQueryName) ) ? getQueryAlias () : "qrslt";

        if( S.isNullOrEmpty(qa) )
            sb.append(sql);
        else
        {
            if( mrk == null )
                sb.append( "select * from (" ).append(sql).append(") ").append(qa);
            else
                sb.append( "select ").append(mrk).append(",").append(qa).append(".* from (" ).append(sql).append(") ").append(qa);
        }

        if( rowData )
        {
            final IEntityProperty<? super P, ?> ep = getCompositeAdapter().getIdProperty();
            sb.append("\nwhere ").append( ep.getColumnInfo().getName() ).append(" = :").append( ep.getColumnInfo().getName() );
        }
        else
        {
            appendPredicates(sb);

            if( S.isNotNullOrEmpty( getOrderBy() ) )
                sb.append("\nORDER BY ").append( getCompletedOrderBy() );
        }

        return sb.toString();
    }

    /** Обновить текущую запись из БД */
    public void refreshCurrentItemFromDB( boolean refreshDependentData, Consumer<SQLTreeDataSet<P>> onNoDataFound )
    {
        try
        {
            final ITreeDataSetItem<P> currentItem = getCurrentItem();

            if( currentItem == null || currentItem.getValue() == null )
                return;

            final CompositeAdapter<P, ?> adapter = getCompositeAdapter();
            final IEntityProperty<P, ?> idProperty = adapter.getIdProperty();

            final P oldValue = currentItem.getValue();

            final Comparable<?> currentId = adapter.getId(oldValue);

            final Comparable<?> currentParentId = adapter.getParentId(oldValue);

            if( currentId == null )
                throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Cannot refresh tree item with null ID" );

            final String idColumn = idProperty.getColumnInfo().getName();

            final String refreshSql = prepareRowDataSql();

            final IParameters prm = new ParametersByName() {

                final IParameters parameters = getParameters();

                @Override
                public Object getParameter(String parameterName)
                {
                    if( idColumn.equalsIgnoreCase(parameterName) )
                        return currentId;

                    return parameters.getParameter(parameterName);
                }

                @Override
                public Object getParameter(int index)
                {
                    return parameters.getParameter(index);
                }
            };

            final JDBCDataReader<P> recordReader;

            try {
                PreparedSql preparedSql = new SqlSelect(
                        refreshSql,
                        getTaskContextForUse().dialect()
                ).prepare();

                recordReader = new JDBCDataReader<>(
                        getTaskContextForUse().getConnection(),
                        preparedSql,
                        getRowMapper(),
                        getRowClass(),
                        isEnableMark()
                );
            }
            catch( Throwable th ) {
                throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Error while preparing SQL statement to read record", th );
            }

            final List<P> items;

            try( final JDBCDataReader<P> reader = recordReader )
            {
                reader.executeQuery(prm);
                items = reader.getNextPage(2);
            }

            if( items.size() > 1 )
                throw new TreeDataSetException( Tags.PRODUCT_LABEL + "More than one row found while refreshing tree item. ID: " + currentId + ", RowClass: " + getRowClass().getName() );

            if( items.isEmpty() )
            {
                if( onNoDataFound != null )
                    onNoDataFound.accept(this);
                else
                    JInvDbException.throwNoDataFound( refreshSql, Collections.emptyList() );

                return;
            }

            final P newValue = items.get(0);

            final Comparable<?> refreshedId = adapter.getId(newValue);
            final Comparable<?> refreshedParentId = adapter.getParentId(newValue);

            if( !U.equals(currentId, refreshedId) )
                throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Refreshed row ID differs from current tree item ID. Current: " + currentId + ", refreshed: " + refreshedId );

            if( !U.equals(currentParentId, refreshedParentId) )
                throw new TreeDataSetException(
                        Tags.PRODUCT_LABEL
                                + "Tree item parent ID changed during refresh. "
                                + "Current: " + currentParentId
                                + ", refreshed: " + refreshedParentId
                                + ". Use executeQuery() to rebuild tree structure"
                );

            final List<ITreeDataSetItem<P>> eventItems = Collections.singletonList(currentItem);

            if( refreshDependentData )
                fireRowsEvent( new TreeDataSetRowsEvent<>( this, true, REFRESH, eventItems, -1 ) );

            currentItem.setValue(newValue);

            if( refreshDependentData )
                fireRowsEvent( new TreeDataSetRowsEvent<>( this, false, REFRESH, eventItems, -1 ) );
        }
        catch( Throwable th ) {
            throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Error on refresh current treeItem data from DB. RowClass = " + getRowClass(), th );
        }
    }


    /** Перед первым выполнением запроса DataSet */
    protected void onBeforeFirstExecute( ) {
    }

    /** */
    private int incrementCounter() {
        int execute_count = U.nvl(getProperty("ts.execute_count"), 0) + 1;
        setProperty ( "ts.execute_count", execute_count );
        return execute_count;
    }

    /**
     * Возвращает значение индексированного параметра.
     * <p>
     * SQLDataSet различает ситуацию когда параметр установлен в {@code null} и когда параметр отсутствует.
     * При отсутствии параметра выбрасывается исключение.
     *
     * @param index
     *      индекс параметра
     *
     * @return значение параметра
     *
     * @throws
     *      TreeDataSetException если параметр с таким индексом отсутствует
     */
    protected Object getParameterValue( int index ) {

        Object value = NO_PARAMETER_VALUE;

        IParameters cp = getCallbackParameters();

        if( cp != null && cp.hasParameter(index) )
            value = cp.getParameter(index);

        if( value == NO_PARAMETER_VALUE && dataSetParameters != null && dataSetParameters.hasParameter(index) )
            value = dataSetParameters.getParameter(index);

        if( value == NO_PARAMETER_VALUE )
            throw new TreeDataSetException("throwNoSetParameterValue");
        //DataSetParameterException.throwNoSetParameterValue(index);

        return value;
    }

    /**
     * Возвращает значение именованного параметра.
     * <p>
     * SQLDataSet различает ситуацию когда параметр установлен в {@code null} и когда параметр отсутствует.
     * При отсутствии параметра выбрасывается исключение.
     *
     * @param name
     *     имя параметра
     *
     * @return значение параметра
     *
     * @throws
     *     TreeDataSetException если параметр с таким именем отсутствует
     */
    protected Object getParameterValue( String name ) {

        Object value = NO_PARAMETER_VALUE;

        IParameters cp = getCallbackParameters();

        if( cp != null && cp.hasParameter(name) )
            value = cp.getParameter(name);

        if( value == NO_PARAMETER_VALUE && dataSetParameters != null && dataSetParameters.hasParameter(name) )
            value = dataSetParameters.getParameter(name);

        if( value == NO_PARAMETER_VALUE )
            throw new TreeDataSetException( Tags.PRODUCT_LABEL + "No parameter value for '" + name +"'");

        return value;
    }

    /** */
    protected IParameters getParameters( )
    {
        return new IParameters( ) {
            @Override
            public Object getParameter( String name ) {
                return getParameterValue( name );
            }
            @Override
            public Object getParameter( int index ) {
                return getParameterValue( index );
            }
        };
    }

    /** */
    protected boolean isRootValue( Comparable value )
    {
        if( value == null )
            return false;

        for( Comparable c : rootIdValues )
        {
            if( c == null )
                continue;

            if( value.compareTo(c) == 0 )
                return true;
        }

        return false;
    }

    private BiFunction<Comparable<?>,Comparable<?>,Boolean> RCValue = ( id, parentId ) -> isRootValue(id);

    private void populate( JDBCDataReader<P> dataReader ) throws TreeDataSetException
    {
        final Iterator<P> iter = dataReader.getNextDataPart(-1);
        final BiFunction<Comparable<?>,Comparable<?>,Boolean> rootCheck =
                rootIdValues == ROOT_NULL_VALUE ? RCNull : RCValue;
        populate(iter, rootCheck );
    }


    /** {@inheritDoc} */
    @Override
    public void executeQuery() throws TreeDataSetException {

        final int executeCount = incrementCounter();
        final boolean firstExecute = executeCount == 1;

        try {

            if( firstExecute )
            {
                fireDataSetEvent( FIRST_TIME_EXECUTE, true );
                onBeforeFirstExecute();
            }

            fireDataSetEvent( EXECUTE, true );

            final IRowMapper<P> rowMapper = getRowMapper();
            final String sql = prepareCompleteSql();
            final PreparedSql preparedSql = new SqlSelect(
                    sql,
                    getTaskContextForUse().dialect()
            ).prepare();

            try( final JDBCDataReader<P> dataReader = new JDBCDataReader<>(
                    getTaskContextForUse().getConnection(),
                    preparedSql,
                    rowMapper,
                    getRowClass(),
                    isEnableMark() ))
            {
                dataReader.executeQuery( getParameters() );

                if(!predicates.isEmpty())
                    predicates.values().stream().filter(PredicateItem::isPending).forEach(PredicateItem::makeFinal);

                final Object oldCurrentId = getCurrentItemId();

                clear();

                populate( dataReader );

                if( !restoreCurrentItemById(oldCurrentId) )
                {
                    if(!rootList.isEmpty() )
                        rootList.get(0).setItemCurrent();
                }

                if( firstExecute )
                    fireDataSetEvent( FIRST_TIME_EXECUTE, false );

                fireDataSetEvent( EXECUTE, false );
            }
            catch( Throwable th ) {

                if(!predicates.isEmpty())
                    predicates.entrySet().removeIf(entry -> entry.getValue().isPending());

                throw th;
            }
        }
        catch (Throwable th) {
            throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Error in SQLTreeDataSet on executeQuery", th );
        }
    }


    /**
     * <h6>Возвращает часть SQL выражения в части 'SELECT' отвечающая за пометку.</h6>
     * <p>
     * Необходимо, если производный DataSet поддерживает пометку.
     *
     * @return строка с частью SQL выражения
     */
    protected String getMarkSQLPart( ) {
        return null;
    }

    @Override
    public void dump( Map<String,Object> d ) {

        super.dump(d);

        IParameters p = new IParameters( ) {
            @Override
            public Object getParameter( String parameterName ) {
                return getParameterValue( parameterName );
            }
            @Override
            public Object getParameter( int parameterIndex ) {
                return getParameterValue( parameterIndex );
            }
        };
        try {

            String strSQL = prepareCompleteSql();
            d.put("ds.sql.query",  strSQL );

            SQLParser s = new SQLParser( strSQL );
            d.put("ds.sql.query_with_parameters",  s.prepareSqlWithParametersValue(p) );
            d.put("ds.support_mark",  isSupportMark() );
            d.put("ds.native_query_name", S.nvl(nativeQueryName) );
            d.put("ds.dataQueryMode", IDataSet.DataQueryModeEnum.QUERY_ALL_ROWS);

            ITreeDataSetItem currentRow = getCurrentItem();
            d.put("zs.currentRow",     S.nvl( currentRow ));
            if( currentRow != null )
                d.put("zs.currentRowData", getPojoString(currentRow.getValue()));
        }
        catch( Exception ex ) {
            d.put( "Error on prepare dump", "X" );
            d.put( "Exception", ex );
        }
    }


}
