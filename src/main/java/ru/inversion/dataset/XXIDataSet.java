package ru.inversion.dataset;

import ru.inversion.dataset.aggr.*;
import ru.inversion.dataset.impl.JDBCDataReader;
import ru.inversion.dataset.impl.SelectBuilder;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.dataset.impl.XXIDsMarkerDao;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.dataset.mark.MarkDescriptor;
import ru.inversion.dataset.mark.MarkModeEnum;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.db.rs.RSUtils;
import ru.inversion.property.IProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.lstn.IListenerManConsumer;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static ru.inversion.dataset.DataSetEvent.DataSetEventType.EXECUTE;
import static ru.inversion.dataset.DataSetMarkEvent.MarkActionEnum.*;
import static ru.inversion.dataset.ISQLDataSet.PrepareSQLModeEnum.MARK_CLEAR;
import static ru.inversion.dataset.ISQLDataSet.PrepareSQLModeEnum.MARK_INSERT;
import static ru.inversion.dataset.mark.MarkModeEnum.*;

/**
 * Реализация {@link IXXIDataSet}
 * <p>
 * @author sulimoff
 */
public class XXIDataSet<T> extends SQLDataSet<T> implements IXXIDataSet<T> {

    /**
     * Слушатели пометки.
     */
    final private IListenerManEvent<IDataSetMarkListener<T>, DataSetMarkEvent<T>> markListeners =
            ListenerManFactory.createListenerManEvent(IDataSetMarkListener::markAction);

    @Override
    public void dump( final Map<String, Object> d ) {
        super.dump( d );
        if ( isSupportMark() ){
            d.put("ds.mark_mode",  getMarkMode() );
            d.put("ds.marker_id",  getMarkerID() );
//            d.put("ds.marked_rows_count",  computeNumberMarkedRows() );
        }
    }

    /**
     * Признак используется ли автофильтр.
     */
	private boolean enableAutoFilter = false;

    /**
     * ID маркера пометки.
     */
	protected Long markerID;

    /**
     * Количество отмеченных строк.
     */
    protected boolean useMarkedSet = false;

    /**
     * Параметры пометки для XXIDataSet.
     */
    private MarkDescriptor markDescriptor = MarkDescriptor.g_emptyInstance;

    /** */
	public XXIDataSet( ) {
        super();
	}

    /** */
    public XXIDataSet( Class<? extends T> rowClass  ) {
        super();
        setRowClass( rowClass );
    }

    /** */
    public XXIDataSet( TaskContext tc ) {
        this( tc, null );
    }

    /** */
    public XXIDataSet( TaskContext tc, Class<? extends T> rowClass ) {
        this( tc, rowClass, null );
    }

    /** */
    public XXIDataSet( TaskContext tc, Class<? extends T> rowClass, IParameters parameters ) {
        this( tc, rowClass, false, parameters );
    }

    /** */
    public XXIDataSet( TaskContext tc, Class<? extends T> rowClass, boolean enableAutoFilter ) {
        this( tc, rowClass, enableAutoFilter, null );
    }

    /** */
    public XXIDataSet( TaskContext tc, Class<? extends T> rowClass, boolean enableAutoFilter, IParameters parameters ) {
        super( tc );
        setRowClass( rowClass );
        setCallbackParameters(parameters);
        this.enableAutoFilter = enableAutoFilter;
    }

    /*
    protected String prepareUseMarkedSetPredicate()
    {
        if( !isSupportMark() )
            return null;

        String fltrStr = null;

        switch( getMarkMode() ) {
            case MRK:
                fltrStr = markDescriptor.getMarkKeyColumns()[0] + " in ( select RMRKROWID from v_jf_mrk where imrkmarkerid = :jinv_marker_id )";
                break;
            case MRK_ID:
                fltrStr = markDescriptor.getMarkKeyColumns()[0] + " in ( select IDROW from v_jf_mrk_id where imrkmarkerid = :jinv_marker_id )";
                break;
            case MRK_ID2:
            {
                StringBuilder sb = new StringBuilder();
                sb.append('(')
                  .append(markDescriptor.getMarkKeyColumns()[0])
                  .append(", ")
                  .append(markDescriptor.getMarkKeyColumns()[1])
                  .append(") in ( select IDROW, IDAROW from v_jf_mrk_id2 where imrkmarkerid = :jinv_marker_id )");

                fltrStr = sb.toString();
            }
                break;
            case MRK_U:
                fltrStr = markDescriptor.getMarkKeyColumns()[0] + " in ( select UROW from v_jf_mrk_u where imrkmarkerid = :jinv_marker_id )";
                break;
        }

        return fltrStr;
    }


    public void useMarkedSet( boolean use ) {

        if( useMarkedSet != use )
        {
            useMarkedSet = use;

            if( useMarkedSet ) {
                String s = prepareUseMarkedSetPredicate();
                if( s != null )
                    filters.put(-2, new FltrItem(s,false) );
            }
            else
                filters.remove(-2);

            needReCreateReader();
        }
    }

    public boolean isUseMarkedSet() {
        return useMarkedSet;
    }
    */

    /**
     * Первое выполнение запроса dataSet
     * <p>
     * загружаем авто-фильтр и фильтр по умолчанию
     */
    protected void onBeforeFirstExecute( ) throws DataSetException {

        if( isEnableAutoFilter() )
        {
            String name = XXIDataSet.this.getName();
            String form = (String)XXIDataSet.this.getProperty("form_name");

            if( U.containsNull( name, form ) )
                return;

            try {

                XXIDsDao.FilterData filterData = null;

                BiFunction<String,String,XXIDsDao.FilterData> handleAutoFilter = (BiFunction< String, String, XXIDsDao.FilterData >)getProperty("ds.handleAutoFilter");

                if( handleAutoFilter == null )
                {
                    // Загрузка автофильтра
                    filterData = XXIDsDao.loadAutoFilter( getTaskContextForUse(), form, name );
                }
                else
                    filterData = handleAutoFilter.apply( form, name );

                if( filterData != null ) {
                    setFilter  ( filterData.getFilterSql(), false, false, true );
                    setProperty("auto_filter_name", filterData.getFilterName() );

                    filterData.forEachParam( (p,v)->setParameter(p.toString(),v) );
                }

                // Загрузка фильтра по умолчанию,
                // тот который настраивается в форме фильтров
                filterData = XXIDsDao.loadDefaultFilter( getTaskContextForUse(), form, name );

                if( filterData != null ) {
                    setFilter( filterData.getFilterSql(), filterData.isFixed(), true, false );
                    setProperty("default_filter_name",    filterData.getFilterName() );
                }

            } catch(Throwable th ) {
                throw new DataSetException( Tags.PRODUCT_LABEL + "Error on load and init autoFilter or defaultFilter", th );
            }
        }
    }

    /**
     * Проверка SQL на котором построен XXIDataSet на то, что будет осуществляться FULLSCAN по "критической" таблице.
     * Используется в формах документов, для оптимизации выполнения запроса.
     *
     * @param tableFor
     *          имя таблицы в БД, по которой возможен FULLSCAN в результате выполнения выражения SQL
     * @return
     *          {@code true } - будет выполнен FULLSCAN
     *          {@code false} - не будет
     *
     * @throws
     *          DataSetException если была ошибка при проверке
     */
    public boolean checkForFullScan( String tableFor ) throws DataSetException {

        if( S.isNullOrEmpty(tableFor) )
            return false;

        try {

            String strSql = prepareCompletedSql( PrepareSQLModeEnum.SHOW );

            SQLParser sp = new SQLParser( strSql );

            final TaskContext tc = getTaskContextForUse();
            final String   stmnt = "{? = call jf_pkg_util.Is_TrnFullScan(?,?)}";

            if( tc.isOracle() )
            {
                //stmnt = "begin ? := sys.diutil.bool_to_int( OPERLIST.Is_TrnFullScan( ?, ? ) ); end;";
                strSql= sp.prepareExplainPlanSql( );
            }
            else
            {
                //stmnt = "{? = call jf_pkg_util.Is_TrnFullScan(?,?)}";
                strSql= sp.prepareSqlWithParametersValue (
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
            }

            try( CallableStatement cs = getTaskContextForUse().getConnection().prepareCall(stmnt) ) {

                cs.registerOutParameter( 1, Types.INTEGER );
                cs.setString( 2, strSql   );
                cs.setString( 3, tableFor );

                cs.executeUpdate();

                return cs.getInt(1) != 0;
            }
        }
        catch( Throwable th ) {
            getTaskContextForUse().rollback();
            throw new DataSetException( Tags.PRODUCT_LABEL + "checkForFullScan", th );
        }
    }

    /**
     * {@inheritDoc }
     * <p>
     * Расширяет базовый метод, добавляет возможность генерации SQL для пометки всех записей.
     */
    @Override
    protected String prepareCompletedSql( PrepareSQLModeEnum prepareMode ) throws DataSetException {

        if( U.in( prepareMode, MARK_INSERT, MARK_CLEAR ) ) {

            if( isSupportMark() )
            {
                if( prepareMode == MARK_INSERT )
                {
                    final StringBuilder sb = new StringBuilder("insert into ") ;

                    String minusSql = null;

                    switch( markDescriptor.getMarkMode() ) {
                        case MRK:
                            sb.append("v_jf_mrk( imrkmarkerid, RMRKROWID ) select :jinv_marker_id, ").append( markDescriptor.getMarkKeyColumns()[0] );
                            //minusSql = "\nMINUS\nselect imrkmarkerid, RMRKROWID from v_jf_mrk where imrkmarkerid = :jinv_marker_id";
                            minusSql = "\nwhere " + markDescriptor.getMarkKeyColumns()[0] + " not in ( select RMRKROWID from v_jf_mrk where imrkmarkerid = :jinv_marker_id )";
                            break;
                        case MRK_ID:
                            sb.append("v_jf_mrk_id( Idmarker, IDROW ) select :jinv_marker_id, ").append( markDescriptor.getMarkKeyColumns()[0] );
                            minusSql = "\nwhere " + markDescriptor.getMarkKeyColumns()[0] + " not in ( select IDROW from v_jf_mrk_id where Idmarker = :jinv_marker_id )";
                            //minusSql = "\nMINUS\nselect Idmarker, IDROW from v_jf_mrk_id where Idmarker = :jinv_marker_id";
                            break;
                        case MRK_ID2:
                            sb.append("v_jf_mrk_id2( Idmarker, IDROW, IDAROW ) select :jinv_marker_id, ")
                              .append(markDescriptor.getMarkKeyColumns()[0])
                              .append(", ")
                              .append(markDescriptor.getMarkKeyColumns()[1]);
                            minusSql = "\nwhere ( " + markDescriptor.getMarkKeyColumns()[0] + ", " +  markDescriptor.getMarkKeyColumns()[1] + ") "
                                        + " not in ( select IDROW, IDAROW from v_jf_mrk_id2 where Idmarker = :jinv_marker_id )";
                            //minusSql = "\nMINUS\nselect Idmarker, IDROW, IDAROW from v_jf_mrk_id2 where Idmarker = :jinv_marker_id";
                            break;
                        case MRK_U:
                            sb.append("v_jf_mrk_u( Idmarker, UROW ) select :jinv_marker_id, ").append( markDescriptor.getMarkKeyColumns()[0] );
                            //minusSql = "\nMINUS\nselect Idmarker, UROW from v_jf_mrk_u where Idmarker = :jinv_marker_id";
                            minusSql = "\nwhere " + markDescriptor.getMarkKeyColumns()[0] + " not in ( select UROW from v_jf_mrk_u where Idmarker = :jinv_marker_id )";
                            break;
                    }

                    SelectBuilder ssb = getSelectBuilder();

                    ssb.hint       ( getOptimizerHint() );

                    //Predicate<PredicateItem> p = item -> !ignoreFilters || !item.isFilter();

                    if(!predicates.isEmpty() )
                        predicates.values().stream().map(i->i.getFilterStr()).forEach( s->ssb.addPredicat(s) );

                    /*
                       .addPredicat( autoFilter         )
                       .addPredicat( getWherePredicat() )
                       .addPredicat( filters.values().stream().map( (f)->f.getFilterStr() ).collect( Collectors.toList() ) );
                    */

                    //String alias

                    sb.append( " FROM (" )
                      .append( ssb.build() )
                      .append(" ) ").append( "qrslt" );

                    if( minusSql != null )
                        sb.append( minusSql );

                    return sb.toString();
                }
                else // MARK_CLEAR
                {
                    final StringBuilder sb = new StringBuilder("delete from ") ;

                    switch( markDescriptor.getMarkMode() ) {
                        case MRK:
                            sb.append("v_jf_mrk where ( imrkmarkerid, RMRKROWID ) in ( select :jinv_marker_id, ").append( markDescriptor.getMarkKeyColumns()[0] );
                            break;
                        case MRK_ID:
                            sb.append("v_jf_mrk_id where ( Idmarker, IDROW ) in ( select :jinv_marker_id, ").append( markDescriptor.getMarkKeyColumns()[0]);
                            break;
                        case MRK_ID2:
                            sb.append("v_jf_mrk_id2 where ( Idmarker, IDROW, IDAROW ) in ( select :jinv_marker_id, ")
                              .append(markDescriptor.getMarkKeyColumns()[0])
                              .append(", ")
                              .append(markDescriptor.getMarkKeyColumns()[1]);
                            break;
                        case MRK_U:
                            sb.append("v_jf_mrk_u where ( Idmarker, UROW ) in ( select :jinv_marker_id, ").append(markDescriptor.getMarkKeyColumns()[0]);
                            break;
                    }

                    SelectBuilder ssb = getSelectBuilder();

                    ssb.hint( getOptimizerHint() );
                    if( !predicates.isEmpty() )
                         predicates.values().stream().map(i->i.getFilterStr()).forEach( s->ssb.addPredicat(s) );

                    sb.append(" FROM( ").append( ssb.build() ).append(") qm) ");

                    return sb.toString();
                }//end MARK_CLEAR
            }
            return null;
        }
        return super.prepareCompletedSql( prepareMode );
    }

	/** {@inheritDoc} */
	@Override
	public boolean isEnableAutoFilter() {
		return enableAutoFilter;
	}
	/** */
	public void setEnableAutoFilter( boolean enableAutoFilter ) {
		this.enableAutoFilter = enableAutoFilter;
	}

    /** */
    private String sql4MarkColumn;

    /** {@inheritDoc } */
    @Override
    public boolean hasParameter( String name )
    {
        return "jinv_marker_id".equals( name ) || super.hasParameter(name);
    }

    /** {@inheritDoc } */
    @Override
    public Object getParameterValue( String name )
    {
        return "jinv_marker_id".equals( name ) ? getMarkerID( ) : super.getParameterValue(name);
    }

    /** {@inheritDoc } */
    @Override
    protected String getMarkSQLPart( )
    {
        if( !isSupportMark() || getMarkerID( ) == null )
            return null;

        if( sql4MarkColumn == null)
        {
            if ( markDescriptor.getMarkMode() == MRK )
            {
                StringBuilder sb = new StringBuilder();
                sb.append( "nvl( (select 1 from dual where exists ( select 1 from v_jf_mrk a$ where a$.IMRKMARKERID = :jinv_marker_id" )
                  .append( " and a$.RMRKROWID = " )
                  .append( markDescriptor.getMarkKeyColumns()[0] )
                  .append( ")),0) ");

                sql4MarkColumn = sb.toString();
            }
            else
            {
                String pkColumns[] = markDescriptor.getMarkKeyColumns( );

                if( pkColumns.length > 0 ) {

                    StringBuilder sb = new StringBuilder();

                    if ( pkColumns.length == 1 ) {

                        if( markDescriptor.getMarkMode() == MRK_ID )
                        {
                            sb.append("nvl((select 1 from dual where exists ( select 1 from v_jf_mrk_id a$ where a$.idmarker = :jinv_marker_id")
                              .append(" and a$.idrow = ")
                              .append(pkColumns[0])
                              .append(")),0) ");
                        }
                        else
                            if ( markDescriptor.getMarkMode() == MRK_U ) {
                                sb.append("nvl((select 1 from dual where exists ( select 1 from v_jf_mrk_u a$ where a$.idmarker = :jinv_marker_id")
                                  .append(" and a$.UROW = ")
                                  .append( pkColumns[0] )
                                  .append(")),0) ");
                        }
                    }
                    else
                    {
                        sb.append("nvl((select 1 from dual where exists ( select 1 from v_jf_mrk_id2 a$ where idmarker = :jinv_marker_id")
                          .append(" and a$.IDROW  = ")
                          .append(pkColumns[0])
                          .append(" and a$.IDAROW = ")
                          .append(pkColumns[1])
                          .append(")),0) ");
                    }
                    sql4MarkColumn = sb.toString();
                }
            }//end else
        }
        return sql4MarkColumn;
    }

    /** {@inheritDoc} */
    @Override
    public void setRowClass( Class rowClass) {

        super.setRowClass(rowClass);

        MarkDescriptor md = ( rowClass == null ? MarkDescriptor.g_emptyInstance : new MarkDescriptor( rowClass ) );
        if( md.getMarkMode() == NONE )
            md = MarkDescriptor.g_emptyInstance;

        markDescriptor = md;
    }

	/**
     * Возвращает и если необходимо инициализирует markerID.
     * <p>
     * @return ID маркера
     *
     * @see #getMarkerID()
     * @see #setMarkerID(java.lang.Long)
     */
	public Long getOrCreateMarkerID( ) {

        if( markerID == null ) {

            markerID = XXIDsMarkerDao.createMarkerID( getTaskContextForUse() );
            needReCreateReader( );
        }

		return markerID;
	}

	/** {@inheritDoc } */
    @Override
    public MarkModeEnum getMarkMode() {
        return markDescriptor.getMarkMode();
    }

	/** {@inheritDoc } */
	@Override
	public Long getMarkerID( ) {
		return markerID;
	}

	/** {@inheritDoc } */
    @Override
    public void setMarkerID( Long markerID ) {
        this.markerID = markerID;
    }

	/** {@inheritDoc } */
    @Override
    public void executeQuery( boolean readAllRecords ) throws DataSetException {
        super.executeQuery( readAllRecords );
        fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum.REFRESH );
    }

    /** {@inheritDoc } */
	@Override
	public void markAllRows( boolean savePrevMarkedRows ) throws DataSetException
    {
        try {
            if( isSupportMark() )
            {
                String strCompletedSQL = prepareCompletedSql( MARK_INSERT );

                XXIDsMarkerDao.markAllRow( getTaskContextForUse(), getOrCreateMarkerID(), markDescriptor, strCompletedSQL,
                    new IParameters() {
                        @Override
                        public Object getParameter( String parameterName ) {
                            return getParameterValue( parameterName );
                        }
                        @Override
                        public Object getParameter( int parameterIndex ) {
                            return getParameterValue( parameterIndex );
                    }},
                    savePrevMarkedRows
                );

                needReCreateReader( );

                super.executeQuery( );

                fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum.MARK_ALL );
            }
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "markAllRow", th );
        }
	}

	/** {@inheritDoc } */
	@Override
	public void unMarkAllRows( boolean savePrevMarkedRows ) throws DataSetException {

        try {

            if( isSupportMark() && getMarkerID() != null ) {

                if( savePrevMarkedRows ) {

                    String strCompletedSQL = prepareCompletedSql( PrepareSQLModeEnum.MARK_CLEAR );

                    XXIDsMarkerDao.unMarkAllRow( getTaskContextForUse(), markDescriptor, strCompletedSQL, new IParameters() {
                        @Override
                        public Object getParameter( String parameterName ) {
                            return getParameterValue( parameterName );
                        }
                        @Override
                        public Object getParameter( int parameterIndex ) {
                            return getParameterValue( parameterIndex );
                    }}
                    );
                }
                else
                    XXIDsMarkerDao.unMarkAllRow( getTaskContextForUse(), getMarkerID(), markDescriptor );

                needReCreateReader( );

                super.executeQuery( );

                fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum.UNMARK_ALL );
            }
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "unMarkAllRow", th );
        }
	}

	/** {@inheritDoc } */
    @Override
    public boolean isSupportMark( ) {
        return markDescriptor.getMarkMode() != NONE;
    }

    /**
     * Реализация установки или снятия пометки на одну запись.
     */
    protected void doMark( T row, int index, boolean mark )  throws DataSetException {

        if( row != null && isSupportMark( ) && ( mark || getMarkerID() != null ) )
        {
            try {

                IMarkable markable = (IMarkable)row;

                if( markable.isMark() != mark ) {
                    XXIDsMarkerDao.markRow( getTaskContextForUse(), getOrCreateMarkerID(), markDescriptor, markable, mark );
                    markable.setMark(mark);

                    fireMarkDataSetEvent( mark ? MARK_ROW : UNMARK_ROW, index, row );

                }//end if
            }
            catch( Throwable th ) {
                throw new DataSetException( Tags.PRODUCT_LABEL + "unMarkAllRow", th );
            }
        }//end if
    }

    /**
     * Пометка записи по ее индексу.
     * <p>
     * Метод обращается к ф-ции {@code getRow(index)}, та, в свою очередь, если запись не находит с таким номером номером
     * выбрасывает исключение NoSuchElementException
     *
     * @param index
     *          номер записи в DataSet которую нужно пометить.
     *
     * @see #unMarkRow(int)
     * @see #markCurrentRow()
     * @see #unMarkCurrentRow()
     * @see #getRow(int)
     */
	public void markRow( int index ) throws DataSetException {
        doMark( getRow(index), index,  true );
	}

    /**
     * Снятие пометки записи по ее индексу.
     * <p>
     * Метод обращается к ф-ции {@code getRow(index)}, та, в свою очередь, если запись не находит с таким номером номером
     * выбрасывает исключение NoSuchElementException
     *
     * @param index
     *          номер записи в DataSet с которой нужно снять пометку.
     *
     * @see #markRow(int)
     * @see #markCurrentRow()
     * @see #unMarkCurrentRow()
     * @see #getRow(int)
     */
	public void unMarkRow( int index ) throws DataSetException {
        doMark( getRow(index), index, false);
	}

	/** {@inheritDoc } */
	@Override
	public void markCurrentRow( ) throws DataSetException {
        doMark( getCurrentRow(), getCurrentRowNum(), true );
	}

	/** {@inheritDoc } */
	@Override
	public void unMarkCurrentRow( ) throws DataSetException {
        doMark( getCurrentRow(), getCurrentRowNum(), false);
	}

	/** */
	public boolean isMarkRow( int index ) {
        return isSupportMark() && ((IMarkable)getRow(index)).isMark();
    }

	/** {@inheritDoc } */
    @Override
	public boolean isMarkCurrentRow( ) {

        if( isSupportMark( ) ) {

            T row = getCurrentRow();

            if( row != null )
                return ((IMarkable)row).isMark();
        }
        return false;
	}

	/** {@inheritDoc } */
    @Override
	public void clearMark( ) throws DataSetException {
        if( isSupportMark( ) ) {
            try {
                XXIDsMarkerDao.unMarkAllRow( getTaskContextForUse(), getMarkerID(), markDescriptor );
                fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum.UNMARK_ALL );
            }
            catch( Throwable th ) {
                throw new DataSetException( Tags.PRODUCT_LABEL + "clearMark", th );
            }
        }
	}

	/**
     * Формирование события связанного с установлением или снятием пометки для всех записей.
     * <p>
     * @param markAction
     *          тип действия с пометкой. должно быть {@code MARK_ALL, UNMARK_ALL}
     */
	protected void fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum markAction ) {

        if( isSupportMark() && !markListeners.isEmpty() )
		{
              markListeners.fire( new DataSetMarkEvent<>( this, markAction ) );
		}
	}

    /** */
    public boolean hasMarkedRows() {

        if( isSupportMark() ) {

            if( rows().stream().filter(mrkprdkt).findFirst().isPresent() )
                return true;

            //if( dataReader != null && !dataReader.isEOF() )
            {
                return computeNumberMarkedRows( ) > 0L;
            }
        }

        return false;
    }

    /**
     * Подсчитывает и возвращает количество помеченных записей на момент вызова метода.
     * <p>
     */
    public long computeNumberMarkedRows( ) {
        return XXIDsMarkerDao.getTotalMarkRowCount( getTaskContextForUse(), getMarkerID(), markDescriptor );
    }

	/**
     * Формирование события связанного с установлением или снятием пометки для одной записи.
     * <p>
     * @param markAction
     *          тип действия с пометкой. должно быть {@code MARK_ROW, UNMARK_ROW}
     * @param index
     *          индекс помечаемой записи
     * @param row
     *          помечаемая запись
     */
	protected void fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum markAction, int index, T row ) {

        if( isSupportMark() && !markListeners.isEmpty() ) {

            markListeners.fire( new DataSetMarkEvent<>( this, markAction, index, row ) );
        }
	}

    /** {@inheritDoc} */
    @Override
	public void addMarkDataSetListener( IDataSetMarkListener<T> dataSetMarkListener ) {
        markListeners.addListener(dataSetMarkListener);
	}

    /** {@inheritDoc} */
    @Override
	public void removeMarkDataSetListener( IDataSetMarkListener<T> dataSetMarkListener ) {
        markListeners.removeListener(dataSetMarkListener);
	}

    /**
     * Преобразует набор отмеченных записей в фильтр для SQL выражения XXIDataSet.
     * <p>
     * Из отмеченных записей формируется строка для предиката SQL выражения XXIDataSet,
     * и устанавливается в качестве временного фильтра в DataSet.
     * Чтобы полученный фильтр повлиял на набор записей необходимо вызвать {@code executeQuery}
     *
     * @see #setFilter(java.lang.String, boolean, boolean)
     * @see #executeQuery()
     */
    public void saveMark2Filter( ) {

        String sql = XXIDsMarkerDao.saveMarkToFilter( getTaskContextForUse(), getMarkerID(), markDescriptor );

        if( S.isNotNullOrEmpty(sql) )
            setFilter( sql, true, false );
    }

    /** */
    final private static Predicate mrkprdkt = (t)->((IMarkable)t).isMark();

    /** {@inheritDoc} */
    @Override
	public Iterator<T> getMarkedRowIterator( Predicate<T> predicate ) {

        if( !isSupportMark() )
            Collections.<T>emptyIterator();

        if( predicate == null )
            return rows( ).stream( ).filter( mrkprdkt ).iterator();
        else
            return rows( ).stream( ).filter( mrkprdkt.and(predicate) ).iterator();
	}

    /** {@inheritDoc} */
    @Override
	public Iterator<T> createMarkedRSIterator( boolean ignoreFilter ) throws DataSetException {

        if( !isSupportMark() || getMarkerID() == null )
            return Collections.<T>emptyIterator();

		final StringBuilder sb = new StringBuilder( );

        final SelectBuilder ssb = getSelectBuilder( );
        ssb.hint( getOptimizerHint( ) )
           .mark(" 1 " )
           .orderBy( getOrderBy() );

        if( !ignoreFilter ) {
            if(!predicates.isEmpty() )
                predicates.values().stream().map(i->i.getFilterStr()).forEach( s->ssb.addPredicat(s) );

//            filters.values().stream().forEach( (f)->ssb.addPredicat(f.getFilterStr() ) );
//            ssb.addPredicat( getWherePredicat( ) );
//            ssb.addPredicat( autoFilter );
        }

        sb.append(" EXISTS\n");

        String[] markKeyColumns = this.markDescriptor.getMarkKeyColumns();

        if ( markDescriptor.getMarkMode() == MRK )
            sb.append( " ( select 1 from v_jf_mrk a$ where a$.IMRKMARKERID = :jinv_marker_id and a$.RMRKROWID = ").append( ssb.getFromObj() ).append( "." ).append( markDescriptor.getMarkKeyColumns()[0]).append( ")");
        else
            if( markKeyColumns.length > 0 ) {

                if ( markKeyColumns.length == 1 ) {

                    if( markDescriptor.getMarkMode() == MRK_ID )
                        sb.append(" ( select 1 from v_jf_mrk_id a$ where a$.idmarker = :jinv_marker_id and a$.idrow = ").append( ssb.getFromObj() ).append( ".").append( markKeyColumns[0] ).append(")");
                    else
                        if ( markDescriptor.getMarkMode() == MRK_U ) {
                            sb.append(" ( select 1 from v_jf_mrk_u a$ where a$.idmarker = :jinv_marker_id and a$.UROW = ").append( ssb.getFromObj() ).append( ".").append( markKeyColumns[0] ).append(")");
                    }
                }
                else {
                    sb.append(" ( select 1 from v_jf_mrk_id2 a$ where idmarker = :jinv_marker_id")
                      .append(" and a$.IDROW = " ).append( ssb.getFromObj() ).append(".")
                      .append(markKeyColumns[0])
                      .append(" and a$.IDAROW = ").append( ssb.getFromObj() ).append(".")
                      .append(markKeyColumns[1])
                      .append(")");
                }
            }

        ssb.addPredicat(sb.toString() );

        return RSUtils.<T>createIterable( getTaskContextForUse().getConnection(), getRowMapperForUse(), ssb.build(),
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
        ).iterator();
    }

    /** {@inheritDoc} */
    @Override
    public IAggregatorBuilder<T> createMarkedAggregatorBuilder( boolean refreshOnExecute, String aggrTable ) {
        return new MarkAggregatorBuilder( refreshOnExecute, aggrTable );
    }

    /** */
    protected class MarkAggregatorBuilder<T> extends AggregatorBuilder<T> {

        final private String aggrTable;

        public MarkAggregatorBuilder (boolean refreshOnExecute, String aggrTable ) {
            super(refreshOnExecute, false);
            this.aggrTable = aggrTable;
        }
        @Override
        public IAggregator<T> build() throws DataSetException {

            if( aggrList.isEmpty() )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Aggregator list is empty");

            if( aggrList.size() == 1 && predicateList.isEmpty() ) {

                Pair<String, AggrFuncEnum> aggrPair = aggrList.get(0);

                if( aggrPair.second == AggrFuncEnum.COUNT )
                    return new NumberMarkedRowAggregator<>( refreshOnExecute, aggrPair.first );
            }

            return new MarkAggregator( aggrList, predicateList, refreshOnExecute, aggrTable );
        }
    }

    /** */
    private class MarkAggregator<T> implements IAggregator<T> {

        private Map<String,Object> values = null;

        final private IListenerManConsumer<IAggregatorListener> listeners = ListenerManFactory.createListenerManConsumer();

        final private List< Triplet<String, AggrFuncEnum,String> > aggrList;
        final private List<String> predicatList;
        final private String       aggrTable;

        private JDBCDataReader<Map<String,Object>> dataReader;

        /** */
        public MarkAggregator(
            List< Triplet<String, AggrFuncEnum, String> > aggrList,
            List< String > predicatList,
            boolean refreshOnExecute,
            String aggrTable
        )
        {
            this.aggrList     = aggrList;
            this.predicatList = predicatList;
            this.aggrTable    = aggrTable;

            if( refreshOnExecute )
            {
                XXIDataSet.this.addDataSetListener( (IDataSetListener) (DataSetEvent e) -> {
                    if( e.getEventType() == EXECUTE && e.isAfter() ) {
                        try {
                            execute( );
                        } catch( DataSetException ex ) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            }

            XXIDataSet.this.addMarkDataSetListener((IDataSetMarkListener)event -> {

                try {
                    execute( );
                } catch (DataSetException ex) {
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on execute aggregator", ex);
                }

            });

            XXIDataSet.this.needReCreateReader.addListener( new IProperty.ChangeListener<Boolean>() {
                @Override
                public void changed(IProperty<? extends Boolean> property, Boolean oldValue, Boolean newValue) {
                    if( newValue ) {
                        if( dataReader != null )
                            try{ dataReader.close(); } catch( Exception ex ) {}
                        dataReader = null;
                    }
               }
            });

        }

        /** */
        private void clearValues() {
            values = Collections.<String,Object>emptyMap();
            listeners.fire( (IAggregatorListener l)->l.change(this) );
        }

        /** */
        private void prepareReader( ) {

            if( dataReader != null )
                return;

            SelectBuilder ssb = getSelectBuilder( );

            StringBuilder sb = new StringBuilder();

            String[] markKeyColumns = markDescriptor.getMarkKeyColumns();

            String fromObj = S.isNullOrEmpty(aggrTable) ? ssb.getFromObj() : aggrTable;

            if ( markDescriptor.getMarkMode() == MRK )
                sb.append( " exists ( select 1 from v_jf_mrk a$ where a$.IMRKMARKERID = :jinv_marker_id and a$.RMRKROWID = ").append( fromObj ).append( "." ).append( markDescriptor.getMarkKeyColumns()[0]).append( ")");
            else
                if( markKeyColumns.length > 0 ) {

                    if ( markKeyColumns.length == 1 ) {

                        if( markDescriptor.getMarkMode() == MRK_ID )
                            sb.append(" exists ( select 1 from v_jf_mrk_id a$ where a$.idmarker = :jinv_marker_id and a$.idrow = ").append( fromObj ).append( ".").append( markKeyColumns[0] ).append(")");
                        else
                            if ( markDescriptor.getMarkMode() == MRK_U ) {
                                sb.append(" exists ( select 1 from v_jf_mrk_u a$ where a$.idmarker = :jinv_marker_id and a$.UROW = ").append( fromObj ).append( ".").append( markKeyColumns[0] ).append(")");
                        }
                    }
                    else {
                        sb.append(" exists ( select 1 from v_jf_mrk_id2 a$ where idmarker = :jinv_marker_id")
                          .append(" and a$.IDROW = " ).append( fromObj ).append(".")
                          .append(markKeyColumns[0])
                          .append(" and a$.IDAROW = ").append( fromObj ).append(".")
                          .append(markKeyColumns[1])
                          .append(")");
                    }
            }


            ssb.aggrTable  ( aggrTable    ) // fromObj
               .addPredicat( predicatList )
               .addPredicat( sb.toString());

            aggrList.forEach( (p)->ssb.addAggregator( p.getFirst(), p.getSecond(), p.third ) );

            dataReader = new JDBCDataReader<>( getTaskContextForUse().getConnection(), ssb.build(), AggrRSMapper.instance, null, false );

        }
        /** */
        @Override
        public Map<String,Object> getValues() {
            return values;
        }
        /** */
        @Override
        public IDataSet<T> getDataSet() {
            return (IDataSet<T>) XXIDataSet.this;
        }
        /** */
        @Override
        public void execute( ) throws DataSetException {

            if( listeners.isEmpty() || getMarkerID( ) == null )
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

            values = (Map<String,Object>)dataReader.getNextPage(-1).get(0);

            listeners.fire( (IAggregatorListener l)->l.change(this) );
        }

        /** */
        @Override
        public void addListener(IAggregatorListener listener) {
            listeners.addListener(listener);
        }

        /** */
        @Override
        public void removeListener(IAggregatorListener listener) {
            listeners.removeListener(listener);
        }
        /** */
        @Override
        public void close() throws DataSetException {
            dataReader.close();
        }
    }

    /**
     *
     */
    private class NumberMarkedRowAggregator<T> implements IAggregator<T> {

        final private Map<String,Object> values = new HashMap<>();
        final private String             key;

        final private IListenerManConsumer<IAggregatorListener> listeners = ListenerManFactory.createListenerManConsumer();

        /** */
        public NumberMarkedRowAggregator( boolean refreshOnExecute, String key )
        {
            this.key = "A$" + key;

            if( refreshOnExecute )
            {
                XXIDataSet.this.addDataSetListener( (IDataSetListener) (DataSetEvent e) -> {
                    if( e.getEventType() == EXECUTE && e.isAfter() ) {
                        try {
                            execute( );
                        } catch( DataSetException ex ) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            }

            XXIDataSet.this.addMarkDataSetListener( (IDataSetMarkListener)event -> {

                if( U.in( event.getMarkAction(), MARK_ALL, MARK_ROW, UNMARK_ROW ) )
                {
                    try {
                        execute( );
                    } catch (DataSetException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                else if( event.getMarkAction() == UNMARK_ALL ) {
                    clearValues( );
                }//end if
            });
        }

        /** */
        @Override
        public Map<String,Object> getValues() {
            return values;
        }

        /** */
        @Override
        public IDataSet<T> getDataSet() {
            return (IDataSet<T>) XXIDataSet.this;
        }

        /** */
        private void clearValues( ) {
            values.put( key, null );
            listeners.fire( (IAggregatorListener l)->l.change(this) );
        }

        /** */
        @Override
        public void execute() throws DataSetException {

            if( listeners.isEmpty() || getMarkerID( ) == null )
                return;

            values.put( key, computeNumberMarkedRows( ) );

            listeners.fire( (IAggregatorListener l)->l.change(this) );
        }

        /** */
        @Override
        public void addListener(IAggregatorListener listener) {
            listeners.addListener(listener);
        }

        /** */
        @Override
        public void removeListener(IAggregatorListener listener) {
            listeners.removeListener(listener);
        }

        /** */
        @Override
        public void close( ) {
        }
    }
}
