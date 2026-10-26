package ru.inversion.dataset;

import ru.inversion.datacall.CallDescriptor;
import ru.inversion.datacall.CallPrepareContext;
import ru.inversion.datacall.maker.CallMaker_WithParametersValue;
import ru.inversion.dataset.aggr.*;
import ru.inversion.dataset.impl.*;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.select.SqlSelect;
import ru.inversion.dataset.parser.ExprInfo;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.db.JInvDbException;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.entity.ColumnSQLDefinition;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.parser.token.Token;
import ru.inversion.property.BooleanProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.lstn.IListenerManConsumer;
import ru.inversion.utils.lstn.ListenerManFactory;
import ru.inversion.utils.TriFunction;

import javax.persistence.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ru.inversion.dataset.DataSetEvent.DataSetEventType.*;
import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.*;
import static ru.inversion.dataset.IRowMapper.EMPTY_MAPPER;
import static ru.inversion.dataset.ISQLDataSet.PrepareSQLModeEnum.*;

/**
 * DataSet для записей полученных из SQL источника данных.
 * <p>
 * Как правило, это SQL запрос.
 *
 * @param <P>
 *        тип записи DataSet
 *
 * @author Sulimoff
 */
public class SQLDataSet<P> extends AbstractDataSetBase<P> implements ISQLDataSet<P> {

   /**
    * TaskContext для SQLDataSet.
    * <p>
    * Обеспечивает доступ к соединению с БД.
    */
   private TaskContext     taskContext;

   /**
    * SQL выражение на котором построен SQLDataSet.
    * Задается из вне
    */
   protected String        strSQL;

   /**
    * Выражение отвечающее за сортировку в окончательном SQL,
    * на котором строится выборка.
    */
   private   String        orderBYExpression;

   /**
    * Инструкция для оптимизатора RDBMS.
    */
   protected String        optimizerHint;

   /**
    * Query alias.
    */
   protected String		queryAlias;


   /**
    * Имя запроса, если DataSet строится на аннотации NamedNativeQuery
    */
   protected String        nativeQueryName;

   /** Имя native query было вычислено автоматически, а не задано явно. */
   private boolean nativeQueryNameAutoDetected;

   /**
    * Коллекция предикатов
    */
   protected Map<Integer,PredicateItem> predicates = new ObservableMap<>( new TreeMap<>(), new IObservableMap.IChangeMapListener() {
      @Override
      public void onChanged( IObservableMap.MapChangeEvent change ) {
         needReCreateReader();
      }
   });

   @Override
   final public Map< Integer, PredicateItem > predicates() {
      return predicates;
   }

   /**
    * Поставщик данных из SQL источника данных.
    */
   protected ISQLDataReader<P> dataReader;

   /** Актуальный resolver значений, связанный с текущим PreparedSql. */
   private IParameters preparedReaderParameters;

   /** Живое представление параметров DataSet. Явный null имеет приоритет. */
   private final IParameters mainSqlParameters = new IParameters() {
      @Override public Object getParameter(String name) {
         return SQLDataSet.this.getParameterValue(name);
      }
      @Override public Object getParameter(int index) {
         return SQLDataSet.this.getParameterValue(index);
      }
      @Override public boolean hasParameter(String name) {
         IParameters callback = SQLDataSet.this.getCallbackParameters();
         return (callback != null && callback.hasParameter(name))
                 || SQLDataSet.this.hasParameter(name);
      }
      @Override public boolean hasParameter(int index) {
         IParameters callback = SQLDataSet.this.getCallbackParameters();
         return (callback != null && callback.hasParameter(index))
                 || SQLDataSet.this.hasParameter(index);
      }
   };

   /**
    * Размер страницы данных.
    * <p>
    * Кол-во строк записей сколько за раз необходимо загрузить из источника данных при подкачке.
    * По умолчанию устанавливается равным 60.
    */
   private int pageSize = 60;

   /**
    * Маппер для загрузки записей из JDBC ResultSet.
    * <p>
    * Rel-OO маппер - отражение (перевод) данных из JDBC набора ResultSet в тип {@code rowClass} записи SQLDataSet;
    *
    * @see IRowMapper
    */
   private IRowMapper<P> rowMapper;

   /** */
   private DataQueryModeEnum dataQueryMode = DataQueryModeEnum.STANDARD;

   /**
    * Флаг сигнализирующий о том, что необходимо перестроить SQL выражение на котором построен SQLDataSet.
    * (для внутреннего использования)
    */
   final protected BooleanProperty needReCreateReader = new BooleanProperty(false) {
      @Override
      public String getName() {
         return "needReCreateReader";
      }
      @Override
      public Object getBean() {
         return this;
      }
   };

   /**
    * Создает пустой DataSet.
    */
   public SQLDataSet( ) {
      this((Class)null);
   }

   /**
    * Создает пустой DataSet с типом записи.
    * <p>
    * @param rowClass
    *        Тип записи в DataSet
    */
   public SQLDataSet( Class<? extends P >  rowClass ) {
      this( null, rowClass );
   }

   /**
    * Создает пустой DataSet и инициализирует TaskContext.
    * <p>
    * @param tc
    *          TaskContext
    */
   public SQLDataSet( TaskContext tc ) {
      this( tc, null );
   }

   /**
    * Создает пустой DataSet с TaskContext и типом строки.
    * <p>
    * @param tc
    *          TaskContext
    * @param rowClass
    *          Тип записи в DataSet
    */
   public SQLDataSet( TaskContext tc, Class<? extends P >  rowClass ) {
      this( tc, rowClass, null );
   }

   /**
    * Создает пустой DataSet с TaskContext, типом строки и параметрами.
    * <p>
    * @param tc
    * @param rowClass
    * @param parameters
    */
   public SQLDataSet( TaskContext tc, Class<? extends P > rowClass, IParameters parameters ) {
      super( rowClass );
      setTaskContext(tc);
      setCallbackParameters(parameters);

      needReCreateReader.addListener( ( property, oldValue, newValue ) -> refreshCurrentRowSQL = null);
   }

   /** {@inheritDoc } */
   @Override
   public SQLDataSet set( String name, Object value ) {
      super.set( name, value );
      return this;
   }

   /** {@inheritDoc } */
   @Override
   public SQLDataSet< P > set( int index, Object value ) {
      super.set( index, value );
      return this;
   }

   /**
    * Версия метода setName в стиле "Fluent interface".
    * <p>
    * @param name
    *          имя DataSet
    * @return
    *          this
    *
    * @see AbstractDataSetBase#setName
    */
   public SQLDataSet< P > name( String name ) {
      setName(name);
      return this;
   }

   /** {@inheritDoc}  */
   @Override
   public void setRowClass( Class rowClass ) {
      if (getRowClass() != rowClass) {
         // Сначала проверяем инвариант базового DataSet: нельзя менять тип при загруженных строках.
         super.setRowClass(rowClass);
         clearAutoDetectedNativeQueryName();
         exprInfo = null;
         needReCreateReader();
      }
   }

   /**
    * Версия метода setRowClass в стиле "Fluent interface".
    * <p>
    * @param rowClass
    *          тип для записи DataSet
    * @return
    *          this
    *
    * @see AbstractDataSetBase#setRowClass
    */
   public SQLDataSet< P > rowClass( Class<? extends P > rowClass ) {
      setRowClass(rowClass);
      return this;
   }

   /**
    * Устанавливает размер страницы данных при подкачке.
    * Сколько данных за один раз будет подкачено из источника данных.
    * <p>
    * @param pageSize
    *          размер страницы, должен быть > 0
    *
    * @throws IllegalArgumentException
    *          если значение меньше или равно 0
    */
   public void setPageSize( int pageSize ) {

      if( pageSize <= 0 )
         throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "pageSize must be > 0" );

      this.pageSize = pageSize;
   }

   /**
    * Возвращает размер страницы установленный методом {@link #setPageSize(int) }.
    * <p>
    * @return размер страницы.
    */
   public int getPageSize() {
      return pageSize;
   }

   /**
    * Версия метода setPageSize в стиле "Fluent interface".
    * <p>
    * @param pageSize
    *          размер страницы, должен быть > 0
    * @return
    *          this
    *
    * @see #setPageSize
    */
   public SQLDataSet< P > pageSize( int pageSize ) {
      setPageSize(pageSize); return this;
   }

   /**
    * Устанавливает query Alias для запроса.
    * <p>
    * Установка значения отличного от текущего, приводит к сбросу состояния запроса внутри DataSet.
    * см. {@link #needReCreateReader() }
    *
    * @param qa
    *          строка со значением
    *
    * @see #queryAlias
    * @see #getQueryAlias()
    * @see #needReCreateReader()
    */
   public void setQueryAlias( String qa ) {

      String newQueryAlias = ( qa == null ? null : qa.trim() );

      if( S.isNullOrEmpty( qa )  )
         newQueryAlias = null;

      if( !U.equals( newQueryAlias, this.queryAlias ) )
      {
         this.queryAlias = newQueryAlias;

         needReCreateReader( );
      }
   }

   /**
    * Возвращает Query alias для запроса, установленного методом setQueryAlias.
    * <p>
    * @return  queryAlias
    *
    * @see #queryAlias
    * @see #setQueryAlias(java.lang.String)
    */
   public String getQueryAlias( ) {
      return queryAlias;
   }

   /**
    * Версия метода setQueryAlias в стиле "Fluent interface".
    * <p>
    * @param queryAlias
    *
    * @return
    *          this
    *
    * @see #setQueryAlias
    */
   public SQLDataSet< P > queryAlias( String queryAlias ) {
      setQueryAlias(queryAlias);
      return this;
   }


   /**
    * Устанавливает подсказку оптимизатору RDMS для выполнения запроса.
    * <p>
    * Установка подсказки отличной от текущей, приводит к сбросу состояния запроса внутри DataSet.
    * см. {@link #needReCreateReader() }
    *
    * @param oh
    *          строка с подсказкой
    *
    * @see #optimizerHint
    * @see #getOptimizerHint()
    * @see #needReCreateReader()
    */
   public void setOptimizerHint( String oh ) {

      String newOptimizerHint = ( oh == null ? null : oh.trim() );

      if( S.isNullOrEmpty( newOptimizerHint )  )
         newOptimizerHint = null;

      if( !Objects.equals( newOptimizerHint, this.optimizerHint ) )
      {
         this.optimizerHint = newOptimizerHint;

         needReCreateReader( );
      }
   }

   /**
    * Возвращает подсказку для оптимизатора RDBM установленную методом setOptimizerHint.
    * <p>
    * @return  строку подсказки
    *
    * @see #optimizerHint
    * @see #setOptimizerHint(java.lang.String)
    */
   public String getOptimizerHint( ) {
      return optimizerHint;
   }

   /**
    * Версия метода setOptimizerHint в стиле "Fluent interface".
    * <p>
    * @param optimizerHint
    *          строка с подсказкой
    * @return
    *          this
    *
    * @see #setOptimizerHint
    */
   public SQLDataSet< P > optimizerHint( String optimizerHint ) {
      setOptimizerHint(optimizerHint);
      return this;
   }

   /** */
   public void setNativeQueryName( String nqn ) {

      String newNativeQueryName = ( nqn == null ? null : nqn.trim() );

      if( S.isNullOrEmpty( newNativeQueryName )  )
         newNativeQueryName = null;

      // После явного вызова setter имя больше не считается автоматически выбранным,
      // в том числе когда оно совпадает с текущим.
      nativeQueryNameAutoDetected = false;
      if( !Objects.equals( newNativeQueryName, this.nativeQueryName ) )
      {
         this.nativeQueryName = newNativeQueryName;
         exprInfo = null;
         needReCreateReader( );
      }
   }

   /** */
   public String getNativeQueryName( ) {
      return this.nativeQueryName;
   }
   public SQLDataSet<P> nativeQueryName( String nqn ) {
      setNativeQueryName(nqn);
      return this;
   }

   /**
    * Метод сброса флага состояния о том, что SQL выражение изменилось.
    * <p>
    * Используется методами DataSet для уведомления о том, что SQL выражение было изменено
    * и при запросе данных и необходимо пересоздать источник данных.
    *
    * (для внутреннего использования)
    */
   protected void needReCreateReader() {
      // BooleanProperty не уведомляет слушателей при повторной установке true.
      // Зависимый SQL-кеш должен сбрасываться при КАЖДОМ структурном изменении.
      refreshCurrentRowSQL = null;
      refreshCurrentRowPreparedSql = null;
      needReCreateReader.setValue( true );
   }

   /** Удаляем только автоматически определённое имя; явно заданное сохраняем. */
   private void clearAutoDetectedNativeQueryName() {
      if (nativeQueryNameAutoDetected) {
         nativeQueryName = null;
         nativeQueryNameAutoDetected = false;
      }
   }

   /**
    * Устанавливает TaskContext для досутпа к БД.
    * <p>
    * @param taskContext
    *
    * @see TaskContext
    */
   public void setTaskContext( TaskContext taskContext ) {
      if (this.taskContext != taskContext) {
         this.taskContext = taskContext;
         clearAutoDetectedNativeQueryName();
         exprInfo = null;
         needReCreateReader();
      }
   }

   /**
    * Возвращает TaskContext установленный методом {@link #setTaskContext(ru.inversion.tc.TaskContext) }.
    * <p>
    * @return
    *          taskContext
    *
    * @see TaskContext
    */
   public TaskContext getTaskContext( ) {
      return this.taskContext;
   }

   /**
    * Версия метода setTaskContext в стиле "Fluent interface".
    * <p>
    * @param taskContext
    *          taskContext
    * @return
    *          this
    *
    * @see #setTaskContext
    */
   public SQLDataSet< P > taskContext( TaskContext taskContext ) {
      setTaskContext( taskContext );
      return this;
   }

   /**
    * Возвращает TaskContext для использования в методах SQLDataSet.
    * <p>
    * с проверкой его инициализации.
    *
    * @return
    *          taskContext
    * @throws
    *          IllegalStateException - если TaskContext не инициализирован
    *
    * @see TaskContext
    * @see #setTaskContext
    * @see #getTaskContext
    */
   protected TaskContext getTaskContextForUse( ) {

      TaskContext tc = getTaskContext( );

      if( tc == null )
         throw new IllegalStateException( Tags.PRODUCT_LABEL + "SQLDataSet: TaskContext is null" );

      return tc;
   }

   /**
    * Установка Rel-OO маппера для SQLDataSet.
    * <p>
    * После установки сбрасывает флаг состояния SQL выражения.
    * @param rowMapper
    *
    * @see IRowMapper
    * @see #getRowMapper()
    * @see #needReCreateReader()
    */
   public void setRowMapper( IRowMapper< P > rowMapper ) {

      this.rowMapper = rowMapper;

      needReCreateReader( );
   }

   /**
    * Возвращает Rel-OO маппер установленный для SQLDataSet.
    * <p>
    * Если маппер не установлен то пытается получить экземпляр по классу записи из метаданных.
    *
    * @return маппер.
    *
    * @see IRowMapper
    * @see #getRowMapper()
    * @see #needReCreateReader()
    */
   public IRowMapper< P > getRowMapper( ) {
      return rowMapper;
   }

   protected IRowMapper< P > getRowMapperForUse() {

      IRowMapper< P > rowMapper = getRowMapper();

      if( rowMapper == null ) {

         if( Object[].class != getRowClass() )
            rowMapper = EntityMetadataFactory.< P >getEntityMetaData( (Class< P >)getRowClass() ).getRowMapper();
      }

      return rowMapper;
   }

   /**
    * Версия метода setRowMapper в стиле "Fluent interface".
    * <p>
    * @param rowMapper
    *          маппер
    * @return
    *          this
    *
    * @see #setRowMapper
    */
   public SQLDataSet< P > rowMapper( IRowMapper< P > rowMapper ) {
      setRowMapper( rowMapper );
      return this;
   }


   /** {@inheritDoc} */
   public DataQueryModeEnum getDataQueryMode() {
      return this.dataQueryMode;
   }

   /** */
   public void setDataQueryModeEnum ( DataQueryModeEnum dataQueryMode ) {
      this.dataQueryMode = dataQueryMode;
   }

   /** */
   public boolean isQueryAllRows() {
      return getDataQueryMode() == DataQueryModeEnum.QUERY_ALL_ROWS;
   }

   /** */
   public boolean isSingleRow() {
      return getDataQueryMode() == DataQueryModeEnum.SINGLE_ROW;
   }

   /** */
   public SQLDataSet< P > queryAllRows() {
      setDataQueryModeEnum( DataQueryModeEnum.QUERY_ALL_ROWS );
      return this;
   }
   /** */
   public SQLDataSet< P > singleRow() {
      setDataQueryModeEnum( DataQueryModeEnum.SINGLE_ROW );
      return this;
   }

   /**
    * Возвращает признак, что DataSet поддерживает режим пометки записей.
    * <p>
    * В базовой реализации определено, что пометка не поддерживается.
    * Производные классы должны этот метод перекрыть если будут поддерживать пометку.
    *
    * @return признак поддержки пометки
   protected boolean isSupportMark( ) {
   return false;
   }
    */

   /**
    * Возвращает часть SQL выражения в части 'SELECT' отвечающая за пометку.
    * <p>
    * Необходимо если производный DataSet поддерживал пометку.
    *
    * @return строка с частью SQL выражения
    */
   protected String getMarkSQLPart( ) {
      return null;
   }

   /** Отбор столбцов для селекта */
   public final static Predicate<Method> methodFilter = new Predicate< Method >() {
      @Override
      public boolean test( Method m ) {
         if( !m.isAnnotationPresent(Transient.class) ) {
            Column c = m.getAnnotation(Column.class);
            return c != null && !c.name().isEmpty() && S.isNullOrEmpty(c.table());
         }
         return false;
      }
   };


   /** Получение имени столбца из аннотации */
   public final static TriFunction<Method,String,SqlDialect,String> columnMapper = new TriFunction<Method, String, SqlDialect, String>() {

      /** */
      private String getDefinition( Method m, Column c, SqlDialect dialect ) {

         ColumnSQLDefinition cDef = m.getAnnotation(ColumnSQLDefinition.class);

         if( cDef == null || ( cDef.lang().isSpec() && cDef.lang() != dialect.plLang() ) )
            return c.columnDefinition();
         else
            return cDef.columnDefinition();
      }

      /** */
      private String nn( String name, SqlDialect dialect ) {
         return dialect.columnName(name);//columnNameNormalizer.apply(name);
      }

      @Override
      public String apply( Method m, String alias, SqlDialect dialect ) {

         final Column c = m.getAnnotation(Column.class);

         String cDefSQl = getDefinition( m, c, dialect );

         if( S.isNullOrEmpty( cDefSQl ) )
         {
            if( S.isNullOrEmpty(alias) )
               return nn( c.name(), dialect );
            else
               return alias + "." + nn( c.name(), dialect );
         }

         final String def = cDefSQl.trim();

         if( S.isNullOrEmpty(def) || def.length() < 2 )
            return nn( c.name(), dialect );

         return def + ' ' + nn(c.name(),dialect);
      }
   };


   /**
    * Получение текущей строки SQL на которой строится выражение получения данных.
    */
   private String internalGetSQL( ) {

      String sql = getSQL( );

      if( sql != null )
         return sql;

      Class<? extends P > clazz = getRowClass();

      if( clazz == null )
         return null;

      {
         Table table = clazz.getAnnotation(Table.class);

         if( table != null && !S.isNullOrEmpty(table.name()) ) {

            sql = Arrays.asList( clazz.getMethods () )
                    .stream()
                    .filter( methodFilter::test )
                    .map( (m)->columnMapper.apply( m, null, taskContext.dialect() ) )
                    .collect( Collectors.joining( ",", "SELECT ", " FROM " + table.name() ) );
         }//end if
      }

      return sql;
   }

   /**
    * Признак что SQL будет генериться из аннотации @Table и аннотаций @Column над геттерами.
    */
   //private Boolean  isDefSqlByTable = null;
   private ExprInfo exprInfo        = null;

   /** */
   protected boolean isDefSqlByTable( ) {

      if( nativeQueryName == null ) {

         Class rowClass = getRowClass( );

         if( this.strSQL == null && rowClass != null )
         {

            if( XXIDsDao.getNativeQuery(taskContext, rowClass, null ) != null ) {
               nativeQueryName = QUERY_UNNAMED;
               nativeQueryNameAutoDetected = true;
            }
            else if( rowClass.isAnnotationPresent( Table.class ) ) {
               nativeQueryName = QUERY_TABLE;
               nativeQueryNameAutoDetected = true;
            }
                /*
                boolean isDefSqlByTable = rowClass.isAnnotationPresent( Table.class )
                                  &&
                                    !rowClass.isAnnotationPresent( NamedNativeQuery.class  )
                                  &&
                                  XXIDsDao.getNativeQuery(taskContext, rowClass, null ) == null
                                  &&
                                  ( !rowClass.isAnnotationPresent( NamedNativeQueries.class)
                                     ||
                                     this.nativeQueryName == null
                                  );
                */
         }
         else
            return false;
      }
      return nativeQueryName == QUERY_TABLE;
   }

   /** */
   protected SelectBuilder getSelectBuilder( ) {
      for (PredicateItem item : predicates.values()) {
         if (item.getExpression() != null)
            throw new IllegalStateException(
                    "Legacy SelectBuilder cannot bind ISQLExpression parameters");
      }

      final String qa = getQueryAlias();

      SelectBuilder returnBuilder = null;

      if( isDefSqlByTable() ) {
         returnBuilder = new EntitySelectBuilder( getRowClass(), taskContext );
      }
      else
      {

         String sqlText = internalGetSQL();

         if( exprInfo == null ) {
            SQLParser sp  = new SQLParser( sqlText );
            exprInfo  = sp.analyze( );
         }

         if( exprInfo.isMysterious() )
            returnBuilder = new SQLSelectBuilder( sqlText );
         else
            returnBuilder = new SimpleSelectBuilder( sqlText, exprInfo );
      }

      if( S.isNotNullOrEmpty( qa ) )
         returnBuilder.queryAlias(qa);

      return returnBuilder;
   }

   /**
    * Готовит SQL выражение в строковом виде для получения и обработки строк данных DataSet.
    * <p>
    * @param prepareMode
    *          режим подготовки SQL выражения см. {@link PrepareSQLModeEnum}
    * @return
    *          строку с SQL
    *
    * @throws DataSetException
    *          если была ошибка
    */
   protected String prepareCompletedSql( PrepareSQLModeEnum prepareMode ) throws DataSetException {
      return prepareCompletedSql( prepareMode, false );
   }

   private String prepareCompletedSql( PrepareSQLModeEnum prepareMode, boolean ignoreFilters ) throws DataSetException {

      SelectBuilder sb = getSelectBuilder();

      sb.additionalTags( prepareMode == SHOW_WITH_TAGS );

      sb.hint ( getOptimizerHint());

      String sqlMark = isSupportMark( ) ? getMarkSQLPart( ) : null;

      if( sqlMark != null )
         sb.mark( sqlMark );
      else
      if( isSupportMark( ) )
         sb.mark(" 0 ");

        /*
        sb.addPredicat( autoFilter          )
          .addPredicat( getWherePredicat( ) );

        if( !ignoreFilters )
            sb.addPredicat( filters.values().stream().map( (f)->f.getFilterStr() ).collect( Collectors.toList() ) );
        */

      Predicate<PredicateItem> p = item -> !ignoreFilters || !item.isFilter();

      if( !predicates.isEmpty() )
         predicates.values().stream().filter(p).map(i->i.getFilterStr()).forEach( s->sb.addPredicat(s) );

      if( prepareMode == SHOW )
         sb.orderBy( getOrderBy( ) );

      return sb.build( );
   }


   /**
    * Возвращает значение индексированного параметра.
    * <p>
    * SQLDataSet различает ситуацию когда параметр установлен в {@code null} и когда параметр отсутствует.
    * При отсутствии параметра выбрасывается исключение.
    *
    * @param index
    *        индекс параметра
    *
    * @return
    *        значение параметра
    *
    * @throws
    *          DataSetParameterException если параметр с таким индексом отсутствует
   protected Object getParameterValue( int index ) throws DataSetParameterException {

   Object value = NO_PARAMETER_VALUE;

   IParameters cp = getCallbackParameters();

   if( cp != null && cp.hasParameter(index) )
   value = cp.getParameter(index);

   if( value == NO_PARAMETER_VALUE && hasParameter(index) )
   value = getParameter(index);

   if( value == NO_PARAMETER_VALUE )
   DataSetParameterException.throwNoSetParameterValue(index);

   return value;
   }
    */


   /**
    * Возвращает значение именованного параметра.
    * <p>
    * SQLDataSet различает ситуацию когда параметр установлен в {@code null} и когда параметр отсутствует.
    * При отсутствии параметра выбрасывается исключение.
    *
    * @param name
    *          имя параметра
    *
    * @return значение параметра
    *
    * @throws
    *          DataSetParameterException если параметр с таким именем отсутствует
   protected Object getParameterValue( String name ) throws DataSetParameterException {

   Object value = NO_PARAMETER_VALUE;

   IParameters cp = getCallbackParameters();

   if( cp != null && cp.hasParameter(name) )
   value = cp.getParameter(name);

   if( value == NO_PARAMETER_VALUE && hasParameter(name) )
   value = getParameter(name);

   if( value == NO_PARAMETER_VALUE )
   DataSetParameterException.throwNoSetParameterValue(name);

   return value;
   }
    */

   /** {@inheritDoc}
    @Override
    public String getFilter( FilterTypeEnum ... filterType ) {

    if( filterType == null || filterType.length == 0 )
    return null;

    List<String> ls = new ArrayList<>( FilterTypeEnum.values().length );

    for( FilterTypeEnum ft : filterType ) {

    switch( ft ) {
    case AUTO:
    ls.add( this.autoFilter );
    break;
    case FIXED:
    ls.addAll( filters.values().stream().filter( (f)->f.isFixed() ).map( (f)->f.getFilterStr() ).collect( Collectors.toList( ) ) );
    break;
    case TEMPORARY:
    ls.addAll( filters.values().stream().filter( (f)->f.isTemporary() ).map( (f)->f.getFilterStr() ).collect( Collectors.toList() ) );
    break;
    }
    }//end for

    if( ls.isEmpty() )
    return null;

    if( ls.size() == 1 )
    return ls.get(0);

    return ls.stream().filter((s)->S.isNotNullOrEmpty(s)).collect( Collectors.joining(" ) and ( ", "(", ")") );
    }
    */

   /** Перед первым выполнением запроса DataSet */
   protected void onBeforeFirstExecute( ) throws DataSetException {

   }

   /** */
   private int incrementCounter() throws DataSetException {

      Integer execute_count = U.nvl( (Integer)getProperty("ds.execute_count"), Integer.valueOf(0) ) + 1;

      setProperty ( "ds.execute_count", execute_count );

      return execute_count;
   }

   private NextSelectBuilder createNextSelectBuilder( boolean ignoreFilters ) {
      SqlDialect dialect = getTaskContextForUse().dialect();
      NextSelectBuilder builder = new NextSelectBuilder(internalGetSQL(), dialect);
      for (PredicateItem item : predicates.values()) {
         if (ignoreFilters && item.isFilter())
            continue;

         if (item.getExpression() != null) {
            if (!item.getFilterStr().equals(item.getExpression().getSQL()))
               throw new IllegalStateException(
                       "ISQLExpression SQL changed after filter registration");
            builder.addPredicate(item.getExpression());
         } else {
            builder.addPredicate(item.getFilterStr());
         }
      }
      builder.orderBy(getOrderBy());

      if (isSupportMark()) {
         String mark = getMarkSQLPart();
         builder.mark(mark == null ? "0" : mark);
      }

      return builder;
   }

   /**
    * Единственный путь подготовки SELECT для обычного SQLDataSet.
    * Legacy SelectBuilder здесь больше не участвует.
    */
   protected PreparedSql prepareDataQuery() throws DataSetException {
      if (S.isNotNullOrEmpty(getOptimizerHint()))
         throw new UnsupportedOperationException(
                 "optimizerHint is not supported by the PreparedSql pipeline; put the hint in SQL");
      if (S.isNotNullOrEmpty(getQueryAlias()))
         throw new UnsupportedOperationException(
                 "queryAlias is not supported by the PreparedSql pipeline");
      NextSelectBuilder builder = createNextSelectBuilder( false );
      PreparedSql sql = builder.prepare();
      preparedReaderParameters = builder.parameters(mainSqlParameters);
      return sql;
   }

   /** {@inheritDoc} */
   @Override
   public synchronized void executeQuery( boolean fetchAllRows ) throws DataSetException {
      try {

         fireDataSetEvent( DataSetEvent.DataSetEventType.EXECUTE, true );

         // увеличиваем счетчик выполнения запросов в dataSet
         int executeCount = incrementCounter( );

         if( executeCount == 1 )
         {
            fireDataSetEvent( DataSetEvent.DataSetEventType.FIRST_TIME_EXECUTE, true );

            onBeforeFirstExecute( );
         }

         try {

            if( dataReader == null || needReCreateReader.getValue() )
            {
               if (dataReader != null) {
                  dataReader.close();
                  dataReader = null;
               }
               preparedReaderParameters = null;
               PreparedSql sql = prepareDataQuery();

               dataReader = new JDBCDataReader<>(
                       getTaskContextForUse().getConnection(),
                       sql, getRowMapper(), getRowClass(), isSupportMark());

               needReCreateReader.setValue(false);
            }

            dataReader.executeQuery(
                    preparedReaderParameters == null ? mainSqlParameters : preparedReaderParameters);

            clear( );
            swappingData( fetchAllRows );

            // Предикаты подтверждаются только после успешной загрузки первой страницы.
            predicates.values().stream().filter(PredicateItem::isPre).forEach(PredicateItem::makeFinal);
         }
         catch( Throwable th ) {
            // При ошибке исполнения ИЛИ чтения первой страницы pre-предикаты откатываются.
            predicates.entrySet().removeIf(entry -> entry.getValue().isPre());
            needReCreateReader();

            // Не оставляем открытый курсор/PreparedStatement после неудачной загрузки.
            ISQLDataReader<P> failedReader = dataReader;
            dataReader = null;
            preparedReaderParameters = null;
            if (failedReader != null) {
               try {
                  failedReader.close();
               } catch (Throwable closeError) {
                  if (th != closeError)
                     th.addSuppressed(closeError);
               }
            }
            throw th;
         }

         if( getLoadedRowCount() > 0 )
            setCurrentRowNum( 0 );

         totalRowCount = -1;

         if( executeCount == 1 )
            fireDataSetEvent( DataSetEvent.DataSetEventType.FIRST_TIME_EXECUTE, false );

         fireDataSetEvent( DataSetEvent.DataSetEventType.EXECUTE, false );

      }
      catch( Throwable th ) {
         throw new DataSetException( Tags.PRODUCT_LABEL + "Error in dataSet on executeQuery", th );
      }
   }

   /** {@inheritDoc} */
   @Override
   public void executeQuery( ) throws DataSetException {
      executeQuery( false );
   }

   /**
    * Версия метода executeQuery в стиле "Fluent interface".
    * <p>
    * @return
    *          this
    *
    * @see #executeQuery
    */
   public SQLDataSet< P > execute( )  throws DataSetException {
      executeQuery( );
      return this;
   }

   /**
    * Версия метода executeQuery(boolean) в стиле "Fluent interface".
    * <p>
    * @return
    *          this
    *
    * @see #executeQuery(boolean)
    */
   public SQLDataSet< P > execute( boolean not_use )  throws DataSetException {
      executeQuery( not_use );
      return this;
   }

   /**
    * Возвращает SQL строку из аннотации NamedNativeQuery над классом Pojo.
    * <p>
    * @see NamedNativeQuery
    * @see NamedNativeQueries
    */
   private String getNativeQuery( ) {
      assert this.nativeQueryName != QUERY_TABLE;
      return getRowClass() == null ? null : XXIDsDao.getNativeQuery( getTaskContext(), getRowClass(), this.nativeQueryName == QUERY_UNNAMED ? null : this.nativeQueryName  );
   }

   /** {@inheritDoc} */
   @Override
   public String getSQL( ) {
      return U.nvl( strSQL, getNativeQuery() );
   }

   /** {@inheritDoc} */
   @Override
   public void setSQL( String strSQL ) {
      this.strSQL = strSQL;
      clearAutoDetectedNativeQueryName();
      exprInfo = null;  // Старый SQLParser/SelectBuilder не должен использовать анализ предыдущего SQL.
      needReCreateReader();
   }

   /**
    * Версия метода setSQL(String) в стиле "Fluent interface".
    * <p>
    * @return
    *          this
    *
    * @see #setSQL(String)
    */
   public SQLDataSet< P > sql( String strSQL ) {
      setSQL( strSQL );
      return this;
   }

   /** {@inheritDoc} */
   @Override
   public void setOrderBy( String ob ) {

      String newOrderBYExpression = ( ob == null ? null : ob.trim() );

      if( S.isNullOrEmpty( newOrderBYExpression )  )
         newOrderBYExpression = null;

      if( !Objects.equals( newOrderBYExpression, this.orderBYExpression ) )
      {
         this.orderBYExpression = newOrderBYExpression;

         needReCreateReader();
      }
   }

   /** {@inheritDoc} */
   @Override
   public String getOrderBy( ) {
      return orderBYExpression;
   }


   /**
    * Версия метода setOrderBy(String) в стиле "Fluent interface".
    * <p>
    * @return
    *          this
    *
    * @see #setOrderBy(String)
    */
   public SQLDataSet< P > orderBy( String orderBYExpression ) {
      setOrderBy(orderBYExpression);
      return this;
   }

   /** {@inheritDoc} */
   @Override
   public void setWherePredicat( String wherePredicate ) {
      setWherePredicat(wherePredicate, false );
   }

   /** {@inheritDoc}
    @Override
    public String removeWherePredicat( int predicatId ) {

    this.completeWherePredicate = null;

    needReCreateReader( );

    return this.wherePredicates.remove( predicatId );
    }
    */

   /** {@inheritDoc}
    public void setWherePredicat( ISQLExpression sqlExpression ) {

    if( sqlExpression == null || S.isNullOrEmpty( sqlExpression.getSQL() ) )
    setWherePredicat( (String)null );
    else
    {
    String predicat = sqlExpression.getSQL();

    if( predicat.indexOf('?') > 0 || predicat.indexOf(':') > 0 ) {

    SQLParser  sp = new SQLParser( predicat );
    predicat = sp.prepareSqlWithParametersValue( sqlExpression );
    }

    setWherePredicat(predicat);
    }
    }
    */

   /** {@inheritDoc}
    @Override
    public String getWherePredicat( ) {

    if( completeWherePredicate == null && !predicates.isEmpty() ) {

    //if( !wherePredicates.isEmpty() )
    completeWherePredicate =
    predicates.values().stream()
    .filter(e->e.getType()==PredicateTypeEnum.WHERE_PREDICATE).map(e->e.getFilterStr()).collect( Collectors.joining( " ) and ( ", "(", ")") );
    }

    return completeWherePredicate;
    }
    */

   /**
    * Версия метода setWherePredicat(String) в стиле "Fluent interface".
    * <p>
    * @return
    *          this
    *
    * @see #setWherePredicat(String)
    */
   public SQLDataSet< P > wherePredicat( String wherePredicat ) {
      setWherePredicat(wherePredicat);
      return this;
   }

   /** {@inheritDoc} */
   @Override
   public int setFilter( ISQLExpression sqlExpression, boolean fixed, boolean append ) throws DataSetException {
      if (sqlExpression == null || S.isNullOrEmpty(sqlExpression.getSQL()))
         return 0;

      String sql = sqlExpression.getSQL();
      // Старый setFilter(String) переносит ORDER BY в другое поле,
      // поэтому с параметризованным ISQLExpression такую трансформацию
      // до выделения отдельного SQL-aware split делать нельзя.
      if (orderByPattern.matcher(removeNestedParentheses(sql)).find())
         throw new DataSetException(
                 "Embedded ORDER BY in ISQLExpression filter is not supported");

      int id = setFilter(sql, fixed, append);
      if (id > 0) {
         PredicateItem previous = predicates.get(id);
         if (previous == null)
            throw new IllegalStateException("Filter was not registered: " + id);
         predicates.put(id, new PredicateItem(sqlExpression, previous.getType()));
      }
      return id;
   }

   /** {@inheritDoc} */
   @Override
   public int setFilter( String filterString, boolean fixed, boolean append ) {
      return setFilter( filterString, fixed, append, false );
   }

   static final private Pattern orderByPattern = Pattern.compile( "(?i)\\W*order\\s+by\\s*" );
   private final static Pattern parenthesis = Pattern.compile("\\s*\\([^()]*\\)");

   protected int setFilter( String filterString, boolean fixed, boolean append, boolean auto ) {

      String newfilterString = ( filterString == null ? null : filterString.trim() );

      if( S.isNullOrEmpty( newfilterString )  )
         return 0;

      int filterId = -1;

      if( !auto ) {

         filterId = g_filterIDSequence.incrementAndGet();

         if(fixed) {
            if(!append) {
               predicates.entrySet().removeIf(( t ) -> t.getValue().isFixed() && !t.getValue().isLink() );
            }
         } else {
            if(!append) {
               predicates.entrySet().removeIf(( t ) -> t.getValue().isTemporary());
            }
         }
      }

      //
      // Если вдруг в фильтре 'order by' кто-то написал
      //
      String bezSkobok = removeNestedParentheses(newfilterString);
      Matcher orderByMatcher = orderByPattern.matcher( bezSkobok );

      if(  orderByMatcher.find() && orderByMatcher.group() != null  )
      {
            /*
            SQLQueryStatement qs = new SQLQueryStatement( newfilterString ); // <-- туда не лазить

            BiConsumer<INode, PrepareContext > sqlMaker = new SQLMaker_WithParametersValue() {
                @Override
                public void accept( OrderByNode on, PrepareContext c ) { // <-- и туда не лазить
                    c.append( (char)255 );
                    //super.accept( on, c );
                }
            };
            */

         CallDescriptor d = CallDescriptor.create( filterString, LangEnum.SQL );

         BiConsumer< Token, CallPrepareContext > sqlMaker = new CallMaker_WithParametersValue() {
            @Override
            public void accept( Token token, CallPrepareContext c ) {
               if( token == Token.OrderBy )
                  c.append((char)255);
               else
                  super.accept(token, c);
            }
         };

         TriConsumer< Integer,Comparable,Object > parameterHandler = (a,b,c)->{};
         final CallPrepareContext prepareContext = new CallPrepareContext(sqlMaker, parameterHandler);
         d.preparePLSql( prepareContext );

         String s = prepareContext.getSQL();

         int index = s.indexOf( (char)255 );
         if ( index > -1 ){
            newfilterString = s.substring( 0, index ).trim();

            String orderBy  = "~" + s.substring( index + 1) + "~";

            setOrderBy( orderBy );
         }
      }

      if( !S.isNullOrEmpty(newfilterString ) )
      {
         if( !auto )
            predicates.put( filterId, new PredicateItem( newfilterString, fixed ? PredicateTypeEnum.FIXED_FILTER : PredicateTypeEnum.TEMPORARY_FILTER ) );
         else
            predicates.put( filterId, new PredicateItem( newfilterString, PredicateTypeEnum.AUTO_FILTER ) );

         needReCreateReader( );

         return filterId;
      }
      else
         return -1;
   }

   private String removeNestedParentheses( final String filterString ) {
      Matcher matcher = parenthesis.matcher( filterString );
      if ( matcher.find() ) {
         return removeNestedParentheses( matcher.replaceAll( "" ) );
      }
      return filterString;
   }

   /** {@inheritDoc}
    @Override
    public String removeFilter( int filterId ) {

    FltrItem fi = filters.remove(filterId);

    if( fi != null ) {

    needReCreateReader( );

    return fi.getFilterStr();
    }

    return null;
    }
    */

   /**
    * Версия метода setFilter в стиле "Fluent interface".
    * <p>
    * @return
    *        this
    * @see #setFilter(java.lang.String, boolean, boolean)
    */
   public SQLDataSet<P> filter( String filterString, boolean fixed, boolean append ) {
      setFilter( filterString, fixed, append );
      return this;
   }

   /**
    * Версия метода setFilter в стиле "Fluent interface".
    * <p>
    * @return
    *          this
    *
    * @see #setFilter(ru.inversion.dataset.ISQLExpression, boolean, boolean)
    */
   public SQLDataSet< P > filter( ISQLExpression sqlExpression, boolean fixed, boolean append ) throws DataSetException {
      setFilter( sqlExpression, fixed, append );
      return this;
   }

   /** */
   @Override
   public void clearFilter( boolean allFilters ) {

      if( allFilters ) {

         predicates.entrySet().removeIf (
                 (t) ->
                         ( t.getValue().isFilter()
                                 &&
                                 !t.getValue().isLink()
                         )
         );
      }
      else
         predicates.entrySet().removeIf( (t) -> t.getValue().isTemporary() );

      needReCreateReader( );
   }

   /**
    *  Получить полное выражение OrderBy
    *  <p>
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
    */

   /** {@inheritDoc}
    @Override
    public String getCompletedWherePredicat( ) throws DataSetException {

    try {

    SQLParser sp = new SQLParser( this.prepareCompletedSql( SHOW_WITH_TAGS ) );

    String optimizeSql = sp.prepareSqlWithParametersValue (

    new IParameters( ) {
    @Override
    public Object getParameter( String parameterName ) {
    return getParameterValue( parameterName );
    }
    @Override
    public Object getParameter( int parameterIndex ) {
    return getParameterValue( parameterIndex );
    }
    }
    );

    int index1 = optimizeSql.indexOf("--W[\n");

    if( index1 == -1 )
    return null; // no where part in SQL expr


    index1 += "--W[\n".length();

    int index2 = optimizeSql.indexOf("--W]\n", index1);

    String predicat = optimizeSql.substring( index1, index2 );

    return predicat.replaceFirst( ".*[Ww][Hh][Ee][Rr][Ee]", " ");

    } catch ( Throwable ex) {
    throw new DataSetException( Tags.PRODUCT_LABEL + "Error in dataSet on call 'getCompletedWherePredicat'", ex );
    }
    }
    */

   /** Вернуть полностью готовый SQL, c параметрами */
   @Override
   public String getCompletedSQL( ) {

      try {

         final IParameters p = new IParameters( ) {
            @Override
            public Object getParameter( String parameterName ) {
               return getParameterValue( parameterName );
            }
            @Override
            public Object getParameter( int parameterIndex ) {
               return getParameterValue( parameterIndex );
            }
         };

         SQLParser s = new SQLParser( prepareCompletedSql(SHOW) );
         return s.prepareSqlWithParametersValue(p);
      }
      catch( Exception ex ) {
         throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on getCompletedSQL" );
      }
   }

   private transient String refreshCurrentRowSQL;
   private transient PreparedSql refreshCurrentRowPreparedSql;

   /** {@inheritDoc} */
   public void refreshCurrentRowFromDB( boolean refreshDependentData, Consumer<SQLDataSet> onNoDataFound ) throws DataSetException {

      final P row = getCurrentRow();

      if( row == null )
         return;

      try {

         List<IEntityProperty< P,?>> idPropertyList = EntityMetadataFactory.getEntityMetaData( getRowClass() ).getIDList();

         if( idPropertyList.isEmpty() )
            throw new DataSetException( Tags.PRODUCT_LABEL + "In the class of entry '" + getRowClass().getName() + "' is not defined PK fields" );

         final Map<String,Object> idValuesMap = new HashMap<>( );

         idPropertyList.forEach(new Consumer<IEntityProperty< P,?>>( ) {
            @Override
            public void accept(IEntityProperty< P,?> p) {

               Object value = null;

               try {
                  value = p.invokeGetter(row);
               } catch ( Throwable th ) {
                  throw new RuntimeException( Tags.PRODUCT_LABEL + "Error while retrieving the value PK for row class - " + getRowClass().getName(), th );
               }

               idValuesMap.put( p.getColumnInfo().getName().toUpperCase(Locale.ROOT), value );
            }}
         );

         if( refreshCurrentRowPreparedSql == null )
         {
            StringBuilder sb = new StringBuilder( );

            idPropertyList.forEach( (IEntityProperty p) -> {

               String column = p.getColumnInfo().getName();

               sb.append( column )
                       .append(" = :")
                       .append( column )
                       .append(" and ");
            });

            sb.append("null is null");

            NextSelectBuilder builder =
                    new NextSelectBuilder( internalGetSQL(), getTaskContextForUse().dialect() );
            builder.addPredicate( sb.toString() );

            if( isSupportMark() ) {
               String mark = getMarkSQLPart();
               builder.mark( mark == null ? "0" : mark );
            }

            refreshCurrentRowSQL = builder.buildSql();
            refreshCurrentRowPreparedSql = builder.prepare();
         }

         try (JDBCDataReader<P> recordReader = new JDBCDataReader<>(
                 getTaskContextForUse().getConnection(), refreshCurrentRowPreparedSql,
                 getRowMapper(), getRowClass(), isSupportMark())) {
            recordReader.executeQuery (
                 new IParameters( ) {
                    @Override
                    public Object getParameter( String parameterName ) {
                       String key = parameterName.toUpperCase(Locale.ROOT);
                       return idValuesMap.containsKey(key)
                               ? idValuesMap.get(key) : getParameterValue(parameterName);
                    }
                    @Override
                    public Object getParameter( int parameterIndex ) {
                       return getParameterValue( parameterIndex );
                    }
                    @Override
                    public boolean hasParameter( String parameterName ) {
                       return idValuesMap.containsKey(parameterName.toUpperCase(Locale.ROOT))
                               || mainSqlParameters.hasParameter(parameterName);
                    }
                    @Override
                    public boolean hasParameter( int parameterIndex ) {
                       return mainSqlParameters.hasParameter(parameterIndex);
                    }
                 }
         );

         List< P > item = recordReader.getNextPage(1);

         if( !item.isEmpty() ) {

            P newRow = item.get(0);
            P oldRow = updateCurrentRow( newRow );

            if( refreshDependentData )
               fireRowEvent( REFRESH, oldRow, newRow, getCurrentRowNum(), 1 );
         }
         else {
            if( onNoDataFound != null )
               onNoDataFound.accept(this);
            else
               JInvDbException.throwNoDataFound( refreshCurrentRowSQL, idValuesMap.values() );
         }
         }
      }
      catch( Throwable th ) {
         throw new DataSetException( Tags.PRODUCT_LABEL + "Error refreshing the current record from DB", th );
      }
   }

   @Override
   public void refreshCurrentRowFromDB( boolean refreshDependentData ) throws DataSetException {
      refreshCurrentRowFromDB(refreshDependentData, null);
   }

   /** */
   public void refreshCurrentDependentData( ) {
      P curRow = getCurrentRow( );
      if( curRow != null )
         fireRowEvent( REFRESH, curRow, curRow, getCurrentRowNum(), 1 );
   }

   /**
    * Закрывает SQLDataSet и вязанные с ним ресурсы.
    * кидает событие CLOSE
    */
   @Override
   public void close( ) throws DataSetException {

      if( dataReader != null ) {
         fireDataSetEvent( CLOSE, true  );
         dataReader.close( );
         dataReader = null;
         fireDataSetEvent( CLOSE, false );
      }
   }

   /** */
   private long getMaximumRecordsFetched() {

      final String v = System.getProperty("ru.inversion.db.maximum_records_fetched");

      if( S.isNullOrEmpty(v) )
         return -1L;

      try {
         return TypeConverter.convert( v, Long.class );
      }
      catch( Throwable th ) {
         System.err.println("Error on get 'ru.inversion.db.maximum_records_fetched':");
         th.printStackTrace();
      }
      return -1;
   }

   /** {@inheritDoc} */
   @Override
   public boolean swappingData ( ) throws DataSetException {
      return swappingData(false);
   }

   public boolean swappingData ( boolean fetchAllRows  ) throws DataSetException {

      try {

         final int nRowCount = fetchAllRows ? - 1 : U.<DataQueryModeEnum,Integer>decode (
                 getDataQueryMode(),
                 DataQueryModeEnum.QUERY_ALL_ROWS, -1,
                 DataQueryModeEnum.SINGLE_ROW, 1,
                 getPageSize()
         );

         final long maxFetchSize = getMaximumRecordsFetched();

         if( dataReader != null && !dataReader.isEOF() ) {

            List< P > data = dataReader.getNextPage(nRowCount);

            final int realRowCount = data.size();

            final int rowIndex     = rows().size();

            if( realRowCount > 0 ) {

               // Добавить проверку на MAXIMUM_RECORDS_FETCHED
               if( maxFetchSize > 0 ) {

                  if( rows().size() + realRowCount > maxFetchSize ) {

                     dataReader.close(); dataReader = null;

                     throw new IllegalStateException(Tags.PRODUCT_LABEL + "Exceeded the allowed number of records when reading. Max fetch size: " + maxFetchSize);
                  }

               }

               rows().addAll(data);

               fireRowEvent( INSERT, null, rows().get(rowIndex), rowIndex, realRowCount );
            }

            if( isSingleRow() )
            {
               dataReader.close(); dataReader = null;
            }

            return true;
         }
      } catch( Throwable ex ) {
         throw new DataSetException( Tags.PRODUCT_LABEL + "Error on swap data. dataSetName = " + getName(), ex );
      }
      return false;
   }

   /**
    * Готовит строку фильтра для усиления предиката SQL выражения из элементов {@link IFilterItem} фильтра.
    * <p>
    * Используется при установке фильтра из диалога фильтра
    *
    * @param filterList
    *          список элементов фильтра
    * @return
    *          строку которую можно использовать в SQL предикате, если передана пустая коллекция то возвращается пустая строка - ""
    *
    * @throws DataSetException
    *          если были ошибки при подготовке строки
    *
    * @see IFilterItem
    * @see #applyFilterList(java.util.List)
    */
   public static String filterList2SQLPredicat( List<? extends IFilterItem> filterList ) throws DataSetException {

      if( filterList == null || filterList.isEmpty( )  )
         return S.EMPTY_STRING;

      StringBuilder sb = new StringBuilder();

      for( IFilterItem fi : filterList ) {

         if( fi.getProxyFor() != null ) {
             fi = fi.getProxyFor();
         }

         String column = fi.getColumn();

         if( S.isNullOrEmpty(column) && !fi.isExpression() )
            continue;

         Object value = fi.getValue();

         if( value == null || ( fi.getType() == String.class && value.toString().isEmpty() ) )
            continue;

         if( sb.length() > 0 )
            sb.append(" AND ");

         {

            if( fi.isExpression() )
               sb.append("( ").append( value ).append(" )");
            else {

               if( fi.getType() == String.class || ( value.getClass() == String.class && ((String)value).indexOf('%') != -1 )  )
                  sb.append('(').append( column ).append(" LIKE ").append( SQLParser.value2Str(value) ).append(')');
               else {
                  sb.append('(').append( column ).append(" = " ).append( SQLParser.value2Str(value) ).append(')');
               }
            }
         }
      }//end for

      return sb.toString();
   }

   /**
    * Применяет выражения из элементов {@link IFilterItem} на текущее выражение SQL внутри DataSet.
    * <p>
    * Метод сам вызывает executeQuery
    * @param filterList
    *          список элементов фильтра
    *
    * @return
    *          ID сформированного из {@code filterList} и установленного фильтра
    *
    * @throws DataSetException
    *          Если были ошибки при установки фильтра
    */
   public int applyFilterList( List<IFilterItem> filterList ) throws DataSetException {

      try {

         clearFilter( false );

         if( filterList == null || filterList.isEmpty() ) {
            executeQuery( );
            return 0;
         }


         String filterStr = filterList2SQLPredicat( filterList );

         int filterId = 0;

         if( S.isNotNullOrEmpty(filterStr) )
            filterId = setFilter( filterStr, false, false );

         executeQuery( );

         return filterId;

      } catch( Exception ex ) {
         throw new DataSetException( Tags.PRODUCT_LABEL + "applyFilterList", ex );
      }
   }

   /** {@inheritDoc} */
   @Override
   public int setFilter( List<? extends IFilterItem> filterList, boolean fixed, boolean append ) throws DataSetException {

      if( filterList == null || filterList.isEmpty() )
         return 0;

      try {

         String qa = getQueryAlias( );

         StringBuilder sb = new StringBuilder( );

         for( IFilterItem fi : filterList ) {

            if( fi.getProxyFor() != null )
               fi = fi.getProxyFor();

            String column = fi.getColumn();

            if( S.isNullOrEmpty(column) && !fi.isExpression() )
               continue;

            Object value = fi.getValue();

            if( value == null || ( fi.getType() == String.class && value.toString().isEmpty() ) )
               continue;

            if( sb.length() > 0 )
               sb.append(" AND ");

            if( !S.isNullOrEmpty(column) )
            {
               if( S.isNotNullOrEmpty(qa) )
                  column = " " + qa + "." + column + " ";
               else
                  column = " " + column + " ";
            }

            {
               if( fi.isExpression() )
               {
                  final String exprValue = (String)fi.getValue();

                  if( S.isNullOrEmpty(column) )
                  {
                     sb.append(' ').append( exprValue ).append(' ');
                  }
                  else
                  {
                     if( exprValue.indexOf('#') == -1 )
                        sb.append('(').append( column ).append( exprValue ).append(" )");
                     else
                        sb.append('(').append( exprValue.replace("#", column ) ).append(" )");
                  }
               }
               else
               if ( fi.getValueType() == IFilterItem.ValueTypeEnum.LIST )
               {
                  if( value instanceof String )
                     sb.append( column ).append("in (").append( value ).append(')');
                  else
                  if( value instanceof List && !((List)value).isEmpty() )
                  {
                     List<String>  items = (List<String>)value;
                     StringBuilder sbIn  = new StringBuilder();

                     int ii = 0, il = 0;

                     sb.append(" ( ");

                     for( String s : items ) {

                        if( S.contains( s, '_', '%' ) )
                        {

                           if( il > 0 )
                              sb.append(" OR ");

                           sb.append(column).append(" like ").append(s);

                           il++;

                        }//end if
                        else
                        {
                           if( ii == 0 )
                              sbIn.append( column ).append(" in (").append(s);
                           else
                              sbIn.append( ",").append(s);

                           ii++;

                        }

                     }//end for

                     if( sbIn.length() > 0 )
                        sbIn.append(") ");

                     if( ii > 0 )
                        sb.append(" OR ");

                     sb.append( sbIn.toString() );

                     sb.append(" ) ");

                  }//end if
               }
               else
               // Установка значения
               {
                  //if( fi.getType() == String.class || ( value.getClass() == String.class && ((String)value).indexOf('%') != -1 ) )
                  //if( value.getClass() == String.class && S.contains( (String)value, '%', '_' ) )
                  if( value.getClass() == String.class && S.contains( (String)value, '%', ( fi.isIgnore95LikeSym() ? (char)255 : '_' ) ) )
                     sb.append('(').append( column ).append(" LIKE ").append( SQLParser.value2Str(value) ).append(')');
                  else
                  {
                     sb.append('(').append( column ).append(" = " ).append( SQLParser.value2Str(value) ).append(')');
                  }
               }
            }
         }//end for

         return setFilter( sb.toString(), fixed, append );

      } catch( Throwable th ) {
         throw new DataSetException( Tags.PRODUCT_LABEL + "setFilter, 'List<IFilterItem>' version", th );
      }
   }

   /** */
   @Override
   public Iterator< P > createRSIterator( boolean ignoreFilters ) throws DataSetException {

      NextSelectBuilder builder = createNextSelectBuilder( ignoreFilters );
      PreparedSql preparedSql = builder.prepare();
      IParameters parameters = builder.parameters( mainSqlParameters );

      JDBCDataReader<P> reader = new JDBCDataReader<>(
              getTaskContextForUse().getConnection(),
              preparedSql,
              getRowMapperForUse(),
              null,
              false
      );
      reader.executeQuery( parameters );

      return IDataReader.iterator( reader, 50 );
   }

   /*
   public ResultSetRowsWalker createRSWalker( ) throws DataSetException
   {
      try {
         JDBCDataReader<P> r = new JDBCDataReader<P>(
            getTaskContextForUse().getConnection(),
            prepareCompletedSql( SHOW, false ),
            EMPTY_MAPPER,
            getRowClass(),
            isSupportMark( )
         );
         r.executeQuery(
           new IParameters() {
               @Override
               public Object getParameter( String parameterName ) {
                  return getParameterValue( parameterName );
               }
               @Override
               public Object getParameter( int parameterIndex ) {
                  return getParameterValue( parameterIndex );
               }
            }
         );
         return r.createResultSetRowsWalker();
      }catch( Throwable th ) {
         throw new DataSetException( Tags.PRODUCT_LABEL + "Error while preparing 'ResultSetRowsWalker' to read record", th );
      }
   }
  */

   /** */
   public <K> boolean findRow (
           BiPredicate< P, K > predicat,
           K arg2,
           boolean withCurrent,
           boolean populateIfNeed
   ) throws DataSetException
   {
      if( predicat != null && !isEmpty() )
      {
         List< P > rowList = rows();

         int index = withCurrent ? getCurrentRowNum() : 0;

         do {

            for( ; index < rowList.size(); index ++ ) {

               P row = rowList.get(index);

               if( predicat.test(row, arg2) ) {
                  return setCurrentRowNum(index);
               }

            }//end for

         } while( populateIfNeed && swappingData() );
      }
      return false;
   }

   @Override
   public void dump( Map<String,Object> d )
   {
      super.dump(d);

      IParameters p = new IParameters( ) {
         @Override
         public Object getParameter( String parameterName ) { return getParameterValue( parameterName ); }
         @Override
         public Object getParameter( int parameterIndex ) {
            return getParameterValue( parameterIndex );
         }
      };

      try {

         String strSQL = prepareCompletedSql(SHOW);
         d.put("ds.sql.query",  strSQL );

         SQLParser s = new SQLParser( strSQL );
         d.put("ds.sql.query_with_parameters",  s.prepareSqlWithParametersValue(p) );
         d.put("ds.support_mark",  isSupportMark() );
         d.put("ds.native_query_name", S.nvl(nativeQueryName) );
         d.put("ds.dataQueryMode", getDataQueryMode() );

         P currentRow = getCurrentRow();
         d.put("zs.currentRow",     S.nvl( currentRow ));
         d.put("zs.currentRowData", getPojoString( currentRow ) );
      }
      catch( Exception ex ) {
         d.put( "Error on prepare dump", "X" );
         d.put( "Exception", ex );
      }
   }

   public static <P> String getPojoString( final P currentRow ) {

      if( currentRow == null )
         return S.EMPTY_STRING;

      Class<P> rowClass = (Class<P>) currentRow.getClass();

      final StringBuilder sb = new StringBuilder();

      Collection<? extends IEntityProperty<P,?>> values = EntityMetadataFactory.getEntityMetaData(rowClass).getPropertiesMap().values();

      Map<String,Object> valuesMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

      int maxNameLength  = 0;
      int maxValueLength = 0;

      for( IEntityProperty<P,?> ep : values )
      {
         Object v = ep.invokeGetter(currentRow);

         valuesMap.put( ep.getPropertyName(), v );

         if( ep.getPropertyName().length() > maxNameLength )
            maxNameLength = ep.getPropertyName().length();
         if( S.isString(v) && ((String)v).length() > maxValueLength )
            maxValueLength = ((String)v).length();
      }

      final int n =  maxNameLength + 1;
      if( maxValueLength > 150 )
         maxValueLength = 150;

      final String line = S.space( maxNameLength + maxValueLength + 3, '-' );

      sb.append( '\n' );
      sb.append( line );
      sb.append( '\n' ).append( S.rPad( "Property", n ) ).append(": Value\n");
      sb.append( line ).append('\n');


      valuesMap.forEach(( s, o ) -> sb.append( S.rPad( s, n ) ).append(": ").append( S.nvl(o).replace('\n',' ') ).append('\n'));

      sb.append( line ).append('\n');

      return sb.toString();
   }

   private transient int totalRowCount = -1;

   /**
    * {@inheritDoc}
    */
   @Override
   public int getTotalRowCount( ) {
      return totalRowCount;
   }


   // Todo:
   // 1. Сделать класс дескриптор аггрегатора
   // 2. Хранилище аггрегаторов сделать внутри DataSet
   //    в виде списка, проверять на дублирование
   // 3. при обсчете аггрегатора, если для фильтров и для простого установлен одно и тоже выражение
   //    а, фильтров на DataSet нет, то не вызывать второй
   // 4. если аггрегатор создан, с refreshOnExecute = true, и не вызван execute у DataSet
   //    то не рассчитываем аггрегатор
   // 5. Перенести признаки refreshOnExecute и ignoreFilter в IAggregatorBuilder

   //protected List<IAggregator> aggregators;

   /** */
   @Override
   public IAggregatorBuilder< P > createAggregatorBuilder( boolean refreshOnExecute, boolean ignoreFilter ) {
      return new AggregatorBuilder( refreshOnExecute, ignoreFilter );
   }

   /** */
   protected class AggregatorBuilder<T> implements IAggregatorBuilder<T> {

      protected boolean refreshOnExecute, ignoreFilter;
      final protected List< Triplet<String, AggrFuncEnum, String >> aggrList = new ArrayList<>();
      final protected List< String > predicateList = new ArrayList<>();

      /** */
      public AggregatorBuilder( boolean refreshOnExecute, boolean ignoreFilter ) {
         this.refreshOnExecute = refreshOnExecute;
         this.ignoreFilter     = ignoreFilter;
      }

      /** */
      @Override
      public IDataSet<T> getDataSet( ) {
         return (IDataSet<T>)SQLDataSet.this;
      }

      /** */
      @Override
      public IAggregatorBuilder< T > add( String columnExpr, AggrFuncEnum func, String alias ) {

         if( S.isNullOrEmpty(alias) ) {

            StringBuilder sb = new StringBuilder();

            sb.append( '"'  )
                    .append( "A$" )
                    .append( columnExpr )
                    .append( '"'  );

            alias = sb.toString();
         }

         // Todo:
         // Добавить проверку на дублирование уже существующих аггрегаторов

         aggrList.add( Triplet.makeTriplet( columnExpr, func, alias ) );
         return this;
      }

      /** */
      @Override
      public IAggregatorBuilder<T> addWherePredicate( String wherePredicate ) {
         predicateList.add(wherePredicate);
         return this;
      }

      /** */
      @Override
      public IAggregator<T> build( ) throws DataSetException {
         return new Aggregator( aggrList, predicateList, refreshOnExecute, ignoreFilter );
      }

      /** */
      @Override
      public IDataSet<T> buildDataSet() throws DataSetException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }
   }

   /** */
   private class Aggregator<T> implements IAggregator<T> {

      private Map<String,Object> values = null;

      final private IListenerManConsumer<IAggregatorListener<T>> listeners = ListenerManFactory.createListenerManConsumer();

      final private List<Triplet<String, AggrFuncEnum, String>> aggrList;
      final private List<String> predicateList;

      private JDBCDataReader<Map<String,Object>> dataReader;

      final private boolean           ignoreFilter;

      /** */
      public Aggregator (
              List< Triplet<String, AggrFuncEnum, String> > aggrList,
              List< String > predicateList,
              boolean refreshOnExecute,
              boolean ignoreFilter
      )
      {
         this.aggrList      = aggrList;
         this.predicateList = predicateList;
         this.ignoreFilter  = ignoreFilter;

         if( refreshOnExecute )
         {
            SQLDataSet.this.addDataSetListener( (DataSetEvent e) -> {
               if( e.getEventType() == EXECUTE && e.isAfter() )
               {
                  try {
                     execute( true );
                  } catch( DataSetException ex ) {
                     throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on execute aggregator", ex );
                  }
               }
            });
         }

         SQLDataSet.this.needReCreateReader.addListener(( property, oldValue, newValue ) -> {
            if( newValue ) {
               if( dataReader != null )
                  try{ dataReader.close(); } catch( Exception ex ) {}
               dataReader = null;
            }
         });

         //aggregatorList.add( new WeakReference<>(this) );
      }

      /** */
      public boolean isIgnoreFilter() {
         return ignoreFilter;
      }

      /** */
      @SuppressWarnings("unchecked")
      private void prepareReader( ) {

         if( dataReader != null )
            return;

         if( S.isNotNullOrEmpty(getOptimizerHint()) )
            throw new UnsupportedOperationException(
                    "optimizerHint is not supported by the PreparedSql pipeline; put the hint in SQL");
         if( S.isNotNullOrEmpty(getQueryAlias()) )
            throw new UnsupportedOperationException(
                    "queryAlias is not supported by the PreparedSql pipeline");

         NextSelectBuilder base =
                 new NextSelectBuilder( internalGetSQL(), getTaskContextForUse().dialect() );

         for( PredicateItem item : predicates.values() ) {
            if( ignoreFilter && item.isFilter() )
               continue;
            if( item.getExpression() != null )
               throw new UnsupportedOperationException(
                       "ISQLExpression predicates in aggregator are not yet supported");
            base.addPredicate( item.getFilterStr() );
         }

         StringBuilder sql = new StringBuilder("SELECT ");
         int index = 0;
         for( Triplet<String, AggrFuncEnum, String> aggregate : aggrList ) {
            if( index++ > 0 )
               sql.append(", ");

            if( aggregate.getSecond() != AggrFuncEnum.NONE )
               sql.append( aggregate.getSecond() ).append(' ');

            sql.append('(')
               .append( aggregate.getFirst() )
               .append(") ")
               .append( aggregate.third );
         }

         sql.append(" FROM (")
            .append( base.buildSql() )
            .append(") arslt");

         PreparedSql preparedSql =
                 new SqlSelect( sql.toString(), getTaskContextForUse().dialect() ).prepare();

         dataReader = new JDBCDataReader<>(
                 getTaskContextForUse().getConnection(),
                 preparedSql, AggrRSMapper.instance, null, false );
      }

      /** */
      @Override
      public Map<String,Object> getValues() {
         return values;
      }

      /** */
      @Override
      public IDataSet<T> getDataSet() {
         return (IDataSet<T>) SQLDataSet.this;
      }

      /** */
      @Override
      public void execute( ) throws DataSetException {
         execute( false );
      }

      /** */
      protected void execute( boolean onEvent ) throws DataSetException {

         if( onEvent && ( listeners.isEmpty() || getProperty("ds.execute_count") == null ) )
            return;

         values = null;

         prepareReader( );

         dataReader.executeQuery (
                 new IParameters() {
                    @Override
                    public Object getParameter( String parameterName ) {
                       return getParameterValue( parameterName );
                    }
                    @Override
                    public Object getParameter( int parameterIndex ) {
                       return getParameterValue( parameterIndex );
                    }
                 }
         );

         values = dataReader.getNextPage(1).get(0);

         if( totalRowCount == -1 && !ignoreFilter )
         {
            aggrList.stream()
                    .filter( (a)->a.getSecond() == AggrFuncEnum.COUNT )
                    .findFirst()
                    .map ( (a)-> totalRowCount = U.nvl( TypeConverter.convert( values.get( a.third ), Integer.class ), -1 ) );
         }

         listeners.fire( (IAggregatorListener<T> l)->l.change(this) );
      }

      /** */
      @Override
      public void addListener(IAggregatorListener<T> listener) {
         listeners.addListener(listener);
      }

      /** */
      @Override
      public void removeListener(IAggregatorListener<T> listener) {
         listeners.removeListener(listener);
      }

      /** */
      @Override
      public void close() throws DataSetException {
         dataReader.close();
      }
   }
}
