package ru.inversion.dataset;

import ru.inversion.dataset.aggr.*;
import ru.inversion.dataset.impl.JDBCDataReader;
import ru.inversion.dataset.impl.DataSetSelectBuilder;
import ru.inversion.dataset.impl.PreparedSqlValues;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.dataset.impl.XXIDsMarkerDao;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.dataset.mark.MarkDescriptor;
import ru.inversion.dataset.mark.MarkModeEnum;
import ru.inversion.property.IProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.sparser.SqlScannerFactory;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.select.SqlSelect;
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

            DataSetSelectBuilder builder = createDataSetSelectBuilder(false);
            PreparedSql preparedSql = builder.prepare();
            List<Object> values = builder.values(mainSqlParameters);

            final TaskContext tc = getTaskContextForUse();
            final String stmnt = "{? = call jf_pkg_util.Is_TrnFullScan(?,?)}";

            SqlScanner scanner = SqlScannerFactory.create(tc.dialect());
            final String strSql;
            if( tc.isOracle() )
            {
                strSql = renderExplainPlanSql(preparedSql, scanner);
            }
            else
            {
                strSql = SQLDataSet.renderPreparedSql(
                        preparedSql, values, scanner
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


    static String renderExplainPlanSql(
            PreparedSql preparedSql,
            SqlScanner scanner
    ) {
        String sql = preparedSql.getSql();
        StringBuilder result = new StringBuilder(sql.length() + 32);

        int copiedUntil = 0;
        int parameterIndex = 0;

        for( SqlScanner.Token token : scanner.scan(sql) ) {
            if( token.kind() != SqlScanner.Kind.POSITIONAL_PARAMETER )
                continue;

            if( parameterIndex >= preparedSql.getParameters().size() )
                throw new IllegalStateException(
                        "PreparedSql has more JDBC placeholders than parameters");

            result.append(sql, copiedUntil, token.start());
            result.append(":p").append(++parameterIndex).append(' ');
            copiedUntil = token.end();
        }

        if( parameterIndex != preparedSql.getParameters().size() )
            throw new IllegalStateException(
                    "PreparedSql has fewer JDBC placeholders than parameters");

        result.append(sql, copiedUntil, sql.length());
        return result.toString();
    }

    private DataSetSelectBuilder createMarkSourceBuilder() {
        DataSetSelectBuilder builder = createDataSetSelectBuilder(false);
        builder.mark(null);

        /*
         * Sorting is irrelevant for an unbounded bulk operation and may be
         * expensive. With runtime rowLimit it defines which rows belong to
         * the DataSet and must therefore be preserved.
         */
        if( getRowLimit() == null )
            builder.orderBy((String)null);

        return builder;
    }

    private PreparedSql prepareMarkInsertSql( PreparedSql source ) {
        String[] key = markDescriptor.getMarkKeyColumns();
        StringBuilder sql = new StringBuilder("insert into ");

        switch( markDescriptor.getMarkMode() ) {
            case MRK:
                sql.append("v_jf_mrk( imrkmarkerid, RMRKROWID ) select ?, ").append(key[0]);
                break;
            case MRK_ID:
                sql.append("v_jf_mrk_id( Idmarker, IDROW ) select ?, ").append(key[0]);
                break;
            case MRK_ID2:
                sql.append("v_jf_mrk_id2( Idmarker, IDROW, IDAROW ) select ?, ")
                   .append(key[0]).append(", ").append(key[1]);
                break;
            case MRK_U:
                sql.append("v_jf_mrk_u( Idmarker, UROW ) select ?, ").append(key[0]);
                break;
            default:
                throw new IllegalStateException("Unsupported mark mode " + markDescriptor.getMarkMode());
        }

        sql.append(" FROM (").append(source.getSql()).append(") qrslt");

        switch( markDescriptor.getMarkMode() ) {
            case MRK:
                sql.append("\nwhere ").append(key[0])
                   .append(" not in ( select RMRKROWID from v_jf_mrk where imrkmarkerid = ? )");
                break;
            case MRK_ID:
                sql.append("\nwhere ").append(key[0])
                   .append(" not in ( select IDROW from v_jf_mrk_id where Idmarker = ? )");
                break;
            case MRK_ID2:
                sql.append("\nwhere ( ").append(key[0]).append(", ").append(key[1])
                   .append(") not in ( select IDROW, IDAROW from v_jf_mrk_id2 where Idmarker = ? )");
                break;
            case MRK_U:
                sql.append("\nwhere ").append(key[0])
                   .append(" not in ( select UROW from v_jf_mrk_u where Idmarker = ? )");
                break;
            default:
                break;
        }

        List<Parameter> parameters = new ArrayList<>( source.getParameters().size() + 2 );
        parameters.add( Parameter.named("jinv_marker_id") );
        parameters.addAll( source.getParameters() );
        parameters.add( Parameter.named("jinv_marker_id") );
        return new PreparedSql( sql.toString(), parameters );
    }

    private PreparedSql prepareMarkClearSql( PreparedSql source ) {
        String[] key = markDescriptor.getMarkKeyColumns();
        StringBuilder sql = new StringBuilder("delete from ");

        switch( markDescriptor.getMarkMode() ) {
            case MRK:
                sql.append("v_jf_mrk where ( imrkmarkerid, RMRKROWID ) in ( select ?, ").append(key[0]);
                break;
            case MRK_ID:
                sql.append("v_jf_mrk_id where ( Idmarker, IDROW ) in ( select ?, ").append(key[0]);
                break;
            case MRK_ID2:
                sql.append("v_jf_mrk_id2 where ( Idmarker, IDROW, IDAROW ) in ( select ?, ")
                   .append(key[0]).append(", ").append(key[1]);
                break;
            case MRK_U:
                sql.append("v_jf_mrk_u where ( Idmarker, UROW ) in ( select ?, ").append(key[0]);
                break;
            default:
                throw new IllegalStateException("Unsupported mark mode " + markDescriptor.getMarkMode());
        }

        sql.append(" FROM (").append(source.getSql()).append(") qm)");

        List<Parameter> parameters = new ArrayList<>( source.getParameters().size() + 1 );
        parameters.add( Parameter.named("jinv_marker_id") );
        parameters.addAll( source.getParameters() );
        return new PreparedSql( sql.toString(), parameters );
    }

    /**
     * Bind order of prepareMarkInsertSql:
     * marker id, source SELECT values, marker id.
     */
    static List<Object> markInsertJdbcValues(
            PreparedSql markSql,
            Long markerID,
            List<?> sourceValues
    ) {
        Objects.requireNonNull(markSql, "markSql");
        Objects.requireNonNull(markerID, "markerID");
        Objects.requireNonNull(sourceValues, "sourceValues");

        List<Object> rawValues =
                new ArrayList<>(sourceValues.size() + 2);

        rawValues.add(markerID);
        rawValues.addAll(sourceValues);
        rawValues.add(markerID);

        return PreparedSqlValues.jdbc(markSql, rawValues);
    }

    /**
     * Bind order of prepareMarkClearSql:
     * marker id, source SELECT values.
     */
    static List<Object> markClearJdbcValues(
            PreparedSql markSql,
            Long markerID,
            List<?> sourceValues
    ) {
        Objects.requireNonNull(markSql, "markSql");
        Objects.requireNonNull(markerID, "markerID");
        Objects.requireNonNull(sourceValues, "sourceValues");

        List<Object> rawValues =
                new ArrayList<>(sourceValues.size() + 1);

        rawValues.add(markerID);
        rawValues.addAll(sourceValues);

        return PreparedSqlValues.jdbc(markSql, rawValues);
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

    /**
     * EXISTS predicate for the current mark mode.
     * prefix qualifies DataSet key columns when the predicate is used
     * against an aggregate table.
     */
    private String markExistsPredicate(String prefix) {
        String actualPrefix = prefix == null ? "" : prefix;
        String[] key = markDescriptor.getMarkKeyColumns();

        switch( markDescriptor.getMarkMode() ) {
            case MRK:
                return "exists ( select NULL from v_jf_mrk a$ "
                        + "where a$.IMRKMARKERID = :jinv_marker_id "
                        + "and a$.RMRKROWID = "
                        + actualPrefix + key[0] + ")";

            case MRK_ID:
                return "exists ( select NULL from v_jf_mrk_id a$ "
                        + "where a$.idmarker = :jinv_marker_id "
                        + "and a$.idrow = "
                        + actualPrefix + key[0] + ")";

            case MRK_ID2:
                return "exists ( select NULL from v_jf_mrk_id2 a$ "
                        + "where a$.idmarker = :jinv_marker_id "
                        + "and a$.IDROW = "
                        + actualPrefix + key[0]
                        + " and a$.IDAROW = "
                        + actualPrefix + key[1] + ")";

            case MRK_U:
                return "exists ( select NULL from v_jf_mrk_u a$ "
                        + "where a$.idmarker = :jinv_marker_id "
                        + "and a$.UROW = "
                        + actualPrefix + key[0] + ")";

            default:
                // Compatibility: marked aggregator historically tolerated
                // a DataSet without mark support by producing no predicate.
                return "";
        }
    }

    /** {@inheritDoc } */
    @Override
    public boolean hasParameter( String name ) {
        return "jinv_marker_id".equals(name)
                || super.hasParameter(name);
    }

    /** {@inheritDoc } */
    @Override
    public Object getParameterValue( String name ) {
        return "jinv_marker_id".equals(name)
                ? getMarkerID()
                : super.getParameterValue(name);
    }

    /** {@inheritDoc } */
    @Override
    protected String getMarkSQLPart( ) {
        if( !isSupportMark() || getMarkerID() == null )
            return null;

        return "nvl((select 1 from dual where "
                + markExistsPredicate("")
                + "),0) ";
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
                DataSetSelectBuilder source = createMarkSourceBuilder();
                PreparedSql select = source.prepare();
                Long actualMarkerID = getOrCreateMarkerID();
                PreparedSql markSql = prepareMarkInsertSql(select);

                XXIDsMarkerDao.markAllRow(
                    getTaskContextForUse(),
                    actualMarkerID,
                    markDescriptor,
                    markSql,
                    markInsertJdbcValues(
                            markSql,
                            actualMarkerID,
                            source.values(mainSqlParameters)
                    ),
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

                    DataSetSelectBuilder source = createMarkSourceBuilder();
                    PreparedSql select = source.prepare();
                    PreparedSql markSql = prepareMarkClearSql(select);

                    XXIDsMarkerDao.unMarkAllRow(
                            getTaskContextForUse(),
                            markDescriptor,
                            markSql,
                            markClearJdbcValues(
                                    markSql,
                                    getMarkerID(),
                                    source.values(mainSqlParameters)
                            )
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
                throw new DataSetException( Tags.PRODUCT_LABEL + "markRow", th );
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
            return Collections.<T>emptyIterator();

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

        DataSetSelectBuilder builder = createDataSetSelectBuilder( ignoreFilter );
        builder.mark("1");

        builder.addPredicate(markExistsPredicate(""));

        PreparedSql sql = builder.prepare();
        JDBCDataReader<T> reader = new JDBCDataReader<>(
                getTaskContextForUse().getConnection(),
                sql, getRowMapperForUse(), null, false );
        reader.executeQuery(builder.jdbcValues(mainSqlParameters));

        return IDataReader.iterator( reader, 50 );
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
                throw new IllegalStateException(
                        Tags.PRODUCT_LABEL + "Aggregator list is empty");

            return new MarkAggregator(
                    new ArrayList<>(aggrList),
                    new ArrayList<>(predicateList),
                    refreshOnExecute,
                    aggrTable
            );
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
        private PreparedSql preparedSql;
        private DataSetSelectBuilder aggregateSourceBuilder;

        private final IDataSetListener refreshListener;
        private final IDataSetMarkListener markListener;
        private final IProperty.ChangeListener<Boolean> readerInvalidationListener;
        private boolean closed;

        /** */
        public MarkAggregator(
            List< Triplet<String, AggrFuncEnum, String> > aggrList,
            List< String > predicatList,
            boolean refreshOnExecute,
            String aggrTable
        )
        {
            this.aggrList = Collections.unmodifiableList(
                    new ArrayList<>(aggrList));
            this.predicatList = Collections.unmodifiableList(
                    new ArrayList<>(predicatList));
            this.aggrTable = aggrTable;

            refreshListener = refreshOnExecute
                    ? (IDataSetListener)(DataSetEvent e) -> {
                        if( e.getEventType() == EXECUTE
                                && e.isAfter() ) {
                            try {
                                execute(true);
                            }
                            catch( DataSetException ex ) {
                                throw new RuntimeException(
                                        Tags.PRODUCT_LABEL
                                                + "Error on execute aggregator",
                                        ex);
                            }
                        }
                    }
                    : null;

            if( refreshListener != null )
                XXIDataSet.this.addDataSetListener(refreshListener);

            markListener = (IDataSetMarkListener) event -> {
                try {
                    execute(true);
                }
                catch( DataSetException ex ) {
                    throw new RuntimeException(
                            Tags.PRODUCT_LABEL
                                    + "Error on execute aggregator",
                            ex);
                }
            };
            XXIDataSet.this.addMarkDataSetListener(markListener);

            readerInvalidationListener =
                    (property, oldValue, newValue) -> {
                        if( Boolean.TRUE.equals(newValue) )
                            invalidateReader();
                    };

            XXIDataSet.this.needReCreateReader.addListener(
                    readerInvalidationListener);


        }

        private void requireOpen() {
            if( closed )
                throw new IllegalStateException(
                        "Marked aggregator is closed");
        }

        private void invalidateReader() {
            JDBCDataReader<Map<String,Object>> reader = dataReader;
            dataReader = null;
            preparedSql = null;
            aggregateSourceBuilder = null;

            if( reader == null )
                return;

            try {
                reader.close();
            }
            catch( DataSetException ex ) {
                throw new RuntimeException(
                        Tags.PRODUCT_LABEL
                                + "Error closing marked aggregator reader",
                        ex);
            }
        }

        /** */
        private void prepareReader( ) {

            if( dataReader != null )
                return;

            if( S.isNullOrEmpty(aggrTable) ) {
                aggregateSourceBuilder =
                        new DataSetSelectBuilder(
                                internalGetSQL(),
                                getTaskContextForUse().dialect()
                        );

                for( String predicate : predicatList )
                    aggregateSourceBuilder.addPredicate(predicate);

                aggregateSourceBuilder.addPredicate(
                        markExistsPredicate("")
                );

                preparedSql = prepareAggregateSql(
                        aggrList,
                        aggregateSourceBuilder,
                        getTaskContextForUse().dialect()
                );
            }
            else {
                List<String> predicates =
                        new ArrayList<>(predicatList.size() + 1);

                predicates.addAll(predicatList);
                predicates.add(
                        markExistsPredicate(aggrTable + ".")
                );

                preparedSql = prepareAggregateTableSql(
                        aggrList,
                        aggrTable,
                        predicates,
                        getTaskContextForUse().dialect()
                );
            }

            dataReader = new JDBCDataReader<>(
                    getTaskContextForUse().getConnection(),
                    preparedSql,
                    AggrRSMapper.instance,
                    null,
                    false
            );
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
            execute(false);
        }

        private void execute(boolean onEvent) throws DataSetException {

            requireOpen();

            if( getMarkerID() == null )
                return;

            if( onEvent && listeners.isEmpty() )
                return;

            values = null;

            prepareReader();

            dataReader.executeQuery(
                    aggregateSourceBuilder == null
                            ? PreparedSqlValues.jdbc(
                                    preparedSql,
                                    mainSqlParameters)
                            : aggregateJdbcValues(
                                    preparedSql,
                                    aggregateSourceBuilder,
                                    mainSqlParameters)
            );

            values = (Map<String,Object>)dataReader
                    .getNextPage(1)
                    .get(0);

            listeners.fire(
                    (IAggregatorListener l)->l.change(this));
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
            if( closed )
                return;

            closed = true;

            if( refreshListener != null )
                XXIDataSet.this.removeDataSetListener(refreshListener);

            XXIDataSet.this.removeMarkDataSetListener(markListener);
            XXIDataSet.this.needReCreateReader.removeListener(
                    readerInvalidationListener);

            JDBCDataReader<Map<String,Object>> reader = dataReader;
            dataReader = null;
            preparedSql = null;
            aggregateSourceBuilder = null;

            if( reader != null )
                reader.close();
        }
    }


}
