package ru.inversion.dataset;

import ru.inversion.dataset.aggr.IAggregatorBuilder;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Базовый интерфейс для DataSet где в качестве источника данных SQL выражение.
 * <p>
 * @param <P>
 *        тип записи хранящейся в DataSet
 *
 * @author Sulimoff
 * @version 2.0.1
 */
public interface ISQLDataSet<P> extends IDataSet<P>, ISQLExpression {

    /*
        +------------------------------+
        |         Static Zone          |
        +------------------------------+
    */

    /** константы откуда брать данные для запроса */
    /** по аннотации @Table */
    public final static String QUERY_TABLE   ="$TABLE$";
    /** по аннотации @NamedNativeQuery */
    public final static String QUERY_UNNAMED ="$UNNAMED$";

    /** */
    final static AtomicInteger g_filterIDSequence = new AtomicInteger(0);

    /** */
    enum PredicateTypeEnum {

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
        AUTO_FILTER,

        /** Фильтр от внешнего ключа */
        LINK
    }

   /**
    * Тип фильтра используемых в DataSet.
    * <p>
    * Фильтры в ISQLDataSet - SQL текст, который может использоваться для усиления предиката (where) SQL выражения
    *
    * "Временный" и "постоянный" условное разделение,
    * которое предполагает что "временные" фильтры могут быть сброшены отдельно от всех постоянных
    */
    enum FilterTypeEnum {

        /**
         * Временный
         */
        TEMPORARY,

        /**
         * Постоянный
         */
        FIXED,

        /**
         * Автофильтр - устанавливается автоматически перед использованием {@code ISQLDataSet}
         */
        AUTO
    }

    /*
        +------------------------------+
        |      Class Declare Zone      |
        +------------------------------+
    */

    /**
     * Одно выражение для установленного в DataSet фильтра.
     * <p>
     * Фильтр DataSet - часть предиката (where) SQL выражения на котором построен DataSet
     *
     * Для внутреннего использования.
     */
    class PredicateItem {
        final private String filterStr;
        final private PredicateTypeEnum type;
        final private ISQLExpression expression;
        private boolean pre;
        /** */
        public PredicateItem( String filterStr, PredicateTypeEnum type )
        {
            this.filterStr = filterStr;
            this.type = type;
            this.expression = null;
            this.pre = true;
        }
        /** SQL фиксируется при регистрации; значения expression читаются перед execute. */
        public PredicateItem( ISQLExpression expression, PredicateTypeEnum type )
        {
            this.expression = java.util.Objects.requireNonNull(expression, "expression");
            this.filterStr = java.util.Objects.requireNonNull(expression.getSQL(), "expression.sql");
            this.type = type;
            this.pre = true;
        }
        public ISQLExpression getExpression() {
            return expression;
        }
        /** */
        public PredicateTypeEnum getType() {
            return type;
        }
        /** */
        public boolean isPre() {
            return pre;
        }
        /** */
        public boolean isFinal() {
            return !pre;
        }
        /** */
        public void makeFinal( ) {
            this.pre = false;
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
        public String getFilterStr() {
            return filterStr;
        }
        /** */
        public boolean isFilter() {
            return type == PredicateTypeEnum.FIXED_FILTER || type ==  PredicateTypeEnum.TEMPORARY_FILTER;
        }
        /** */
        public boolean isLink() {
            return filterStr.startsWith("/*P[*/");
        }
    }

    /*
        +------------------------------+
        |     Row Type methods zone    |
        +------------------------------+
    */

    /**
     * Установка Rel-OO маппера для SQLDataSet.
     *
     * @param rowMapper
     *
     * @see IRowMapper
     * @see #getRowMapper()
     */
    void setRowMapper( IRowMapper<P> rowMapper );

    /**
     * Возвращает Rel-OO маппер установленный для SQLDataSet.
     *
     * @return маппер.
     *
     * @see IRowMapper
     * @see #getRowMapper()
     */
    IRowMapper<P> getRowMapper( );

    /*
        +------------------------------+
        |       Parameters zone        |
        +------------------------------+
    */

    Object getParameterValue( int index );
    Object getParameterValue( String name );

    /*
        +------------------------------+
        |  SQL Statement methods zone  |
        +------------------------------+
    */

    /** */
    void setNativeQueryName( String nqn );
    /** */
    String getNativeQueryName( );

	/**
     * Установить SQL выражение на основании которого будут получены данные.
     * <p>
     * только для взрослых
     * @param strSQL
     *        текст SQL выражения
     */
	void setSQL( String strSQL );

    /**
     * Установить выражение сортировки.
     * <p>
     * Устанавливает "order by" часть SQL выражения, используемого для получения данных.
     * {@code null} чтоб очистить "order by" часть.
     *
     * Для того чтобы этот параметр отразился на наборе данных, необходимо вызвать {@link IDataSet#executeQuery}
     *
     * @param orderBYExpression
     *        любое SQL выражение допустимое к использованию
     */
	void setOrderBy ( String orderBYExpression );

    /**
     * Получить выражение сортировки установленное методом {@link #setOrderBy}.
     * <p>
     * @return выражение установленное ранее методом {@code setOrderBy}, {@code null} если не было установлено.
     *
     * @see #setOrderBy
     */
	String getOrderBy( );

    /**
     *  Получить полное выражение OrderBy
     */
    default String getCompletedOrderBy( )
    {
        String orderBy = getOrderBy();

        if( S.isNullOrEmpty(orderBy) )
            return orderBy;

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

    /*
        +------------------------------+
        |     Predicates & Filters     |
        +------------------------------+
    */
        /** */
    Map<Integer,PredicateItem> predicates( );

    /** */
    default int setWherePredicate( String wherePredicate, boolean append )
    {
        final Map< Integer, PredicateItem > prdcts = predicates();

        if( !append && !prdcts.isEmpty() )
            prdcts.entrySet().removeIf( (e)->e.getValue().getType() == PredicateTypeEnum.WHERE_PREDICATE );

        int predicateId = -1;

        if( !S.isNullOrEmpty(wherePredicate) )
        {
            predicateId = g_filterIDSequence.incrementAndGet();

            prdcts.put( predicateId, new PredicateItem( wherePredicate, PredicateTypeEnum.WHERE_PREDICATE ) );
        }

        return predicateId;
    }
    /** in_the_name_of_perfectionism */
    default int setWherePredicat( String wherePredicate, boolean append ) { return setWherePredicate( wherePredicate, append ); }


    /**
     * Установить выражение предиката.
     * <p>
     * Устанавливает "where" часть SQL выражения, используемого для получения данных.
     * {@code null} чтоб очистить "where" часть.
     *
     * Для того чтобы этот параметр отразился на наборе данных, необходимо вызвать {@link IDataSet#executeQuery}
     *
     * @param wherePredicate
     *        любое SQL выражение допустимое к использованию
     */
    default void setWherePredicate( String wherePredicate ) {
        setWherePredicate(wherePredicate, false );
    }
    /** in_the_name_of_perfectionism */
    default void setWherePredicat( String wherePredicate ) { setWherePredicate(wherePredicate); }

    /**
     * Удаляет из выражения часть "where" предиката по его ID.
     * <p>
     * @param predicateId
     *        ID предиката полученного из метода {@link #setWherePredicat(java.lang.String, boolean) }
     * @return
     *        Возвращает текст удаляемой части предиката
     */
    default String removeWherePredicate( int predicateId ) {
        final Map< Integer, PredicateItem > prdcts = predicates();
        return U.callIfNotNull( prdcts.remove( predicateId ), PredicateItem::getFilterStr );
    }
    /** in_the_name_of_perfectionism */
    default String removeWherePredicat( int predicatId ){ return removeWherePredicate(predicatId); }

    /**
     * Получить выражение предиката установленное методом {@link ISQLDataSet#setWherePredicat}.
     * <p>
     * @return выражение установленное ранее методом {@code setWherePredicat}, {@code null} если не было установлено.
     *
     * @see #setWherePredicat
     */
    default String getWherePredicate( )
    {
        final Map< Integer, PredicateItem > prdcts = predicates();
        if( prdcts == null || prdcts.isEmpty() || prdcts.values().stream().filter(e->e.getType()==PredicateTypeEnum.WHERE_PREDICATE).count() == 0L )
            return null;
        return prdcts.values().stream().filter(e->e.getType()==PredicateTypeEnum.WHERE_PREDICATE).map(e->e.getFilterStr()).collect( Collectors.joining( " ) and ( ", "(", ")") );
    }
    /** in_the_name_of_perfectionism */
    default String getWherePredicat() { return getWherePredicate(); }

	/**
     * Устанавливает фильтр на выражение.
     *
     * Рассчитан на прием предиката от пользователя с формы, т.е. если фильтр оказался ошибочный - он убирается из
     * предиката, а выражение откатывается к предыдущему состоянию.
     * Чтобы такого не происходило, предлагается использовать {@link #setWherePredicat(String)}
     * <p>
     * Фильтр - SQL текст, который может усилить where-предикат для SQL выражения.
     *
     * Для того чтобы фильтр отразился на наборе данных, необходимо вызвать {@link IDataSet#executeQuery}
     *
     * @param filterString
     *        строка SQL текста фильтра
     *
     * @param fixed
     *        признак - тип фильтра
     *        {@code true } - фиксированный (живет весь сеанс формы и как бы накапливается),
     *        {@code false} - временный (до следующей установки фильтра, затем он убирается из предиката)
     *
     * @param append
     *        признак - добавить выражение фильтра к уже установленному внутри ISQLDataSet или перезаписать,
     *        {@code true } - добавить к существующему,
     *        {@code false} - перезаписать
     *
     * @return
     *        внутренний ID установленного фильтра
     *
     * @see #removeFilter
     * @see #clearFilter
     * @see #getFilter(FilterTypeEnum...)
     */
    default int setFilter( String filterString, boolean fixed, boolean append ) {

        String newFilterString = ( filterString == null ? null : filterString.trim() );

        if( S.isNullOrEmpty( newFilterString )  )
            return 0;

        int filterId = g_filterIDSequence.incrementAndGet();
        final Map< Integer, PredicateItem > prdcts = predicates();

        if( fixed )
        {
            if(!append)
                prdcts.entrySet().removeIf(( t ) -> t.getValue().isFixed());
        }
        else
        {
            if(!append)
                prdcts.entrySet().removeIf(( t ) -> t.getValue().isTemporary());
        }

        prdcts.put (
            filterId,
            new PredicateItem( newFilterString, fixed ? PredicateTypeEnum.FIXED_FILTER : PredicateTypeEnum.TEMPORARY_FILTER )
        );

        return filterId;
    }

	/**
     * Устанавливает фильтр на выражение.
     * <p>
     * В качестве фильтра используется объект sqlExpression
     *
     * Для того чтобы фильтр отразился на наборе данных, необходимо вызвать {@link IDataSet#executeQuery}
     *
     * @param sqlExpression
     *        Объект реализующий интерфейс {@link ISQLExpression}
     *
     * @param fixed
     *        признак - тип фильтра
     *        {@code true } - фиксированный (живет весь сеанс формы и как бы накапливается),
     *        {@code false} - временный (до следующей установки фильтра, затем он убирается из предиката)
     *
     * @param append
     *        признак - добавить выражение фильтра к уже установленному внутри ISQLDataSet или перезаписать,
     *        {@code true } - добавить к существующему,
     *        {@code false} - перезаписать
     *
     * @return внутренний ID установленного фильтра
     *
     * @throws DataSetException
     *          если были ошибки при работе с {@code sqlExpression}
     *
     * @see #removeFilter
     * @see #clearFilter
     */
    default int setFilter( ISQLExpression sqlExpression, boolean fixed, boolean append ) throws DataSetException {

        if( sqlExpression == null || sqlExpression.getSQL() == null )
            return 0;

        try {

            SQLParser sp = new SQLParser( sqlExpression.getSQL()  );
            String optimizeSql = sp.prepareSqlWithParametersValue( sqlExpression );

            return setFilter( optimizeSql, fixed, append );
        } catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "setFilter, SQLExpression version", th );
        }
    }


	/**
     * Устанавливает фильтр на выражение.
     * <p>
     * В качестве фильтра используется список объектов IFilterItem
     *
     * @see #setFilter(String, boolean, boolean)
     */
    int setFilter( List<? extends IFilterItem> filterList, boolean fixed, boolean append ) throws DataSetException;

    /**
     * Возвращает строку фильтра по его типу.
     * <p>
     * Конкантенирует все строки установленного фильтра определенного типа
     * см {@link FilterTypeEnum}
     *
     * @param filterType
     *        тип фильтра
     *
     * @return сконкантенированную строку фильтра типа {@code filterType}
     */
    default String getFilter( FilterTypeEnum ... filterType )
    {
        if( filterType == null || filterType.length == 0 )
            return null;

        final Map< Integer, PredicateItem > prdcts = predicates();

        final List<String> ls = new ArrayList<>( FilterTypeEnum.values().length );

        for( FilterTypeEnum ft : filterType ) {

            switch( ft ) {
                case AUTO:
                    prdcts.values().stream()
                          .filter( (f)->f.getType() == PredicateTypeEnum.AUTO_FILTER )
                          .map   ( (f)->f.getFilterStr() ).findFirst().map( s->ls.add(s) );
                    break;
                case FIXED:
                    ls.addAll( prdcts.values().stream().filter( (f)->f.isFixed() ).map( (f)->f.getFilterStr() ).collect( Collectors.toList( ) ) );
                    break;
                case TEMPORARY:
                    ls.addAll( prdcts.values().stream().filter( (f)->f.isTemporary() ).map( (f)->f.getFilterStr() ).collect( Collectors.toList() ) );
                    break;
            }
        }//end for

        if( ls.isEmpty() )
            return null;

        if( ls.size() == 1 )
            return ls.get(0);

        return ls.stream().filter((s)-> S.isNotNullOrEmpty(s)).collect( Collectors.joining(" ) and ( ", "(", ")") );
    }

    /**
     * Удаляет ранее установленный фильтр.
     * <p>
     * Удаляет ранее установленный фильтр методами {@code setFilter} по ID полученному из {@code setFilter}
     *
     * Для того чтобы удаление отразилось на наборе данных, необходимо вызвать {@link IDataSet#executeQuery}
     *
     * @param filterId
     *        ID фильтра, который нужно удалить
     *
     * @return
     *        текст SQL выражения удаляемого фильтра.
     */
    default String removeFilter( int filterId )
    {
        final Map< Integer, PredicateItem > prdcts = predicates();

        PredicateItem fi = prdcts.remove(filterId);

        if( fi != null )
            return fi.getFilterStr();

        return null;
    }

	/**
     * Удаляет фильтры.
     * <p>
     * Удаляет фильтры из DataSet установленные ранее методом {@code setFilter}
     *
     * Для того чтобы удаление отразилось на наборе данных, необходимо вызвать {@link IDataSet#executeQuery}
     *
     * @param allFilters
     *         признак - удалять все фильтры или только временные
     *         {@code true } - все фильтры,
     *         {@code false} - только временные
     */
    /** */
    default void clearFilter( boolean allFilters )
    {
        final Map< Integer, PredicateItem > prdcts = predicates();

        if( allFilters )
            prdcts.entrySet().removeIf( (t) -> t.getValue().isFilter() );
        else
            prdcts.entrySet().removeIf( (t) -> t.getValue().isTemporary() );
    }

    /**
     * Возвращает полный предикат (where) установленный на SQL запрос, на котором построен {@code ISQLDataSet}.
     * <p>
     * В выражении учитываются {@code wherePredicate} и все фильтры установленные в {@code ISQLDataSet}
     * Если предикат с параметрами, то подставляются их значения
     *
     * @return сконкантенированную строку предиката, если предикат не был задан то {@code null}
     *
     * @throws DataSetException
     *         если были ошибки при построении строки предиката
     */
    default String getCompletedWherePredicate( ) throws DataSetException {

        try {

            final Map< Integer, PredicateItem > prdcts = predicates();

            if( prdcts.isEmpty() )
                return null;

            final String prdctsStr = prdcts.values().stream().map( e -> e.getFilterStr()).collect(Collectors.joining(") AND (", "(",")"));

            SQLParser sp = new SQLParser( prdctsStr );

            String optimizeSql = sp.prepareSqlWithParametersValue (
                new IParameters( ) {
                    @Override
                    public Object getParameter( String parameterName ) { return getParameterValue( parameterName ); }
                    @Override
                    public Object getParameter( int parameterIndex ) {
                        return getParameterValue( parameterIndex );
                    }
                }
            );

            return optimizeSql;

            /*
            int index1 = optimizeSql.indexOf("--W[\n");

            if( index1 == -1 )
                return null; // no where part in SQL expr


            index1 += "--W[\n".length();

            int index2 = optimizeSql.indexOf("--W]\n", index1);

            String predicat = optimizeSql.substring( index1, index2 );

            return predicat.replaceFirst( ".*[Ww][Hh][Ee][Rr][Ee]", " ");
            */

        } catch ( Throwable ex) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error in dataSet on call 'getCompletedWherePredicate'", ex );
        }
    }
    /** in_the_name_of_perfectionism */
    default String getCompletedWherePredicat( ) throws DataSetException { return getCompletedWherePredicate(); }

    /*
        +------------------------------+
        |      Work with rows zone     |
        +------------------------------+
    */

	/**
     * Обновляет текущую запись в DataSet данными из БД.
     * <p>
     * Формирует и выполняет SQL запрос, для получения полей текущей записи из БД.
     *
     * @param refreshDependentData
     *         признак - нужно ли обновлять зависимые от текущей записи DataSet данные
     *         {@code true } - нужно,
     *         {@code false} - нет
     *
     * @throws DataSetException
     *          если при обновлении записи были ошибки
     */
	void refreshCurrentRowFromDB( boolean refreshDependentData ) throws DataSetException;

	/**
     * Обновляет текущую запись в DataSet данными из БД.
     * <p>
     * Формирует и выполняет SQL запрос, для получения полей текущей записи из БД.
     *
     * @throws DataSetException
     *          если при обновлении записи были ошибки
     *
     * @see #refreshCurrentRowFromDB(boolean)
     */
    default void refreshCurrentRowFromDB( ) throws DataSetException {
        refreshCurrentRowFromDB(false);
    }

    /*
        +------------------------------+
        |      Work with row set       |
        +------------------------------+
    */

    /**
     * Создает {@code AutoCloseableIterator} по всем записям которые могут быть получен на основании SQL выражения внутри DataSet.
     * <p>
     * Предполагается что созданный итератор не влияет на набор данных внутри DataSet и на SQL выражение.
     * Т.е. создается копия данных на основе SQL выражения внутри DataSet.
     * @param ignoreFilters
     *        Признак учитывать/не учитывать установленные фильтры внутри DataSet.
     *        {@code true } - не учитывать
     *        {@code false} - учитывать
     * @return
     *        {@code Iterator} с записями
     * @throws
     *         DataSetException если была ошибка при создании {@code AutoCloseableIterator}
     *
     */
    Iterator<P> createRSIterator( boolean ignoreFilters ) throws DataSetException;

    /*
        +------------------------------+
        |       Aggregator zone        |
        +------------------------------+
    */
    /**
     * Создает билдер аггрегаторов.
     * <p>
     * @param refreshOnExecute
     *        должен ли создаваемый агрегатор обновляться после того как будет вызываться {@code executeQuery}
     * @param ignoreFilter
     *        Признак учитывать/не учитывать установленные фильтры внутри DataSet.
     *        {@code true } - не учитывать
     *        {@code false} - учитывать
     *
     * @return билдер
     */
    IAggregatorBuilder createAggregatorBuilder( boolean refreshOnExecute, boolean ignoreFilter );

    default IAggregatorBuilder createAggregatorBuilder( boolean refreshOnExecute ) {
        return createAggregatorBuilder( refreshOnExecute, false );
    }
}
