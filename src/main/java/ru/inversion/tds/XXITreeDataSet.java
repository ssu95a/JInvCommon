package ru.inversion.tds;

import ru.inversion.dataset.DataSetEvent;
import ru.inversion.dataset.DataSetMarkEvent;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.dataset.impl.XXIDsMarkerDao;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.dataset.mark.MarkDescriptor;
import ru.inversion.dataset.mark.MarkModeEnum;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static ru.inversion.dataset.DataSetMarkEvent.MarkActionEnum.*;
import static ru.inversion.dataset.mark.MarkModeEnum.*;

/** */
public class XXITreeDataSet<P> extends SQLTreeDataSet <P> {

    /**
     * Слушатели пометки.
     */
    final private IListenerManEvent< ITreeDataSetMarkListener<P>, TreeDataSetMarkEvent<P> > markListeners =
            ListenerManFactory.createListenerManEvent(ITreeDataSetMarkListener::markAction);

    /** Признак используется ли автофильтр. */
    private boolean enableAutoFilter = false;

    /** ID маркера пометки. */
    protected Long markerId;

    /** Кол-во помеченных записей */
    transient private int markedCount = 0;

    /** Параметры пометки для XXIDataSet. */
    private MarkDescriptor markDescriptor = MarkDescriptor.g_emptyInstance;

    /** */
    //private IMarkHandler<P> markHandler = null;

    /** */
    public XXITreeDataSet( Class< ? extends P > rowClass ) {
        super();
        setRowClass(rowClass);
    }

    /** */
    public XXITreeDataSet( TaskContext tc, Class<? extends P> rowClass ) {
        this(rowClass);
        setTaskContext(tc);
    }


    @Override
    public void setTaskContext(TaskContext taskContext) {

        final TaskContext currentTaskContext = getTaskContext();

        if( currentTaskContext == taskContext )
            return;

        if( currentTaskContext != null && (getMarkerId() != null || !isEmpty()) )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "TaskContext cannot be changed while XXITreeDataSet has a marker ID or loaded rows" );

        super.setTaskContext(taskContext);
    }


    /** */
    protected void fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum markAction, boolean before, boolean leafOnly ) {

        if( isSupportMark() && !markListeners.isEmpty() )
            markListeners.fire( new TreeDataSetMarkEvent<>( this, markAction, before, markedCount, leafOnly) );
    }


    /**
     * Формирование события связанного с установлением или снятием пометки для одной записи.
     * @param markAction
     *        тип действия с пометкой одной записи, должно быть {@code MARK_ROW, UNMARK_ROW}
     * @param item
     *        помечаемая запись
     */
    protected void fireMarkDataSetEvent( DataSetMarkEvent.MarkActionEnum markAction, ITreeDataSetItem<P> item, boolean before )
    {
        if( isSupportMark() && !markListeners.isEmpty() ) {
            markListeners.fire( new TreeDataSetMarkEvent<>( this, markAction, item, before, markedCount) );
        }
    }

    /** {@inheritDoc} */
    public void addMarkListener( ITreeDataSetMarkListener<P> dataSetMarkListener ) {
        markListeners.addListener(dataSetMarkListener);
    }

    /** {@inheritDoc} */
    public void removeMarkListener( ITreeDataSetMarkListener<P> dataSetMarkListener ) {
        markListeners.removeListener(dataSetMarkListener);
    }

    /** */
    public int getMarkedCount() { return markedCount; }

    /** */
    /*
    public void setMarkHandler( IMarkHandler<P> mh )
    {
        if( this.markHandler != mh  )
        {
            if( mh == null )
            {
                if( this.markHandler != null ) {
                    this.markDescriptor = ((MarkDescriptor.CustomMarkDescriptor)this.markDescriptor).getBase();
                    this.markHandler = null;
                }
            }
            else
            {
                this.markHandler = mh;
                if( this.markDescriptor.getMarkMode() != MRK_CUSTOM )
                    this.markDescriptor = this.markDescriptor.toCustom();
            }
        }
    }

    public IMarkHandler<P> getMarkHandler()
    {
        return this.markHandler;
    }
    */

    /** */
    @Override
    protected Object getParameterValue( String name )
    {
        return "jinv_marker_id".equals( name ) ? getMarkerId( ) : super.getParameterValue(name);
    }

    /** {@inheritDoc} */
    @Override
    public void setRowClass( Class<? extends P>  rowClass) {

        Class<P> currentRowClass = getRowClass();

        if (currentRowClass != null) {
            if (currentRowClass == rowClass) {
                return;
            }

            throw new IllegalStateException(
                    Tags.PRODUCT_LABEL
                            + "XXITreeDataSet rowClass cannot be changed. Current: "
                            + currentRowClass.getName()
                            + ", new: "
                            + (rowClass == null ? "null" : rowClass.getName())
            );
        }

        super.setRowClass(rowClass);

        MarkDescriptor md = ( rowClass == null ? MarkDescriptor.g_emptyInstance : new MarkDescriptor( rowClass ) );
        if( md.getMarkMode() == NONE || md.getMarkMode() == MRK_ID2 ) // ID с двумя полями не поддерживаем
            md = MarkDescriptor.g_emptyInstance;

        markDescriptor = md;
        sql4MarkColumn = null;

        if( isSupportMark() )
        {
            addDataSetListener( new ITreeDataSetListener<P>()
            {
                @Override
                public void dataSetChanged( TreeDataSetEvent<P> e ) {
                    if( e.getEventType() == DataSetEvent.DataSetEventType.EXECUTE && e.isAfter() )
                        recalcMarkedCount();
                }
            });

            addItemListener(event -> {

                if (event.getEventType() != TreeDataSetItemEvent.ItemEventType.CHANGE_VALUE)
                    return;

                final Object oldValue = event.getOldValue();
                final Object newValue = event.getNewValue();

                final boolean oldMarked = oldValue instanceof IMarkable && ((IMarkable) oldValue).isMark();

                final boolean newMarked = newValue instanceof IMarkable && ((IMarkable) newValue).isMark();

                if( oldMarked == newMarked )
                    return;
                if (newMarked)
                    markedCount++;
                else
                    if( markedCount > 0 )
                        markedCount--;
            });
        }
        else
            markedCount = 0;
    }

    /** */
    public boolean isEnableAutoFilter() {
        return enableAutoFilter;
    }

    /** */
    public void setEnableAutoFilter( boolean enableAutoFilter ) {
        this.enableAutoFilter = enableAutoFilter;
    }

    /** */
    public boolean isSupportMark( ) {
        //return getMarkHandler() != null || markDescriptor.getMarkMode() != NONE;
        return markDescriptor.getMarkMode() != NONE;
    }

    /** */
    private void recalcMarkedCount() {

        final int[] count = {0};

        traversal(item -> {
            if (item != null && item.getValue() instanceof IMarkable) {
                IMarkable markable = (IMarkable) item.getValue();

                if (markable.isMark()) {
                    count[0]++;
                }
            }

            return true;
        });

        markedCount = count[0];
    }

    /**
     * Возвращает и если необходимо инициализирует markerId.
     * <p>
     * @return ID маркера
     *
     * @see #getMarkerId()
     * @see #setMarkerID(java.lang.Long)
     */
    public Long getOrCreateMarkerId( ) {

        if( markerId == null )
            markerId = XXIDsMarkerDao.createMarkerID( getTaskContextForUse() );

        return markerId;
    }

    /** {@inheritDoc } */
    public MarkModeEnum getMarkMode() {
        return markDescriptor.getMarkMode();
    }

    /** {@inheritDoc } */
    public Long getMarkerId( ) {
        return markerId;
    }

    /** {@inheritDoc } */
    public void setMarkerID(Long markerId) {

        if( U.equals(this.markerId, markerId) )
            return;

        if( !isEmpty() )
            throw new IllegalStateException(
                Tags.PRODUCT_LABEL + "Marker ID cannot be changed while XXITreeDataSet contains loaded rows"
            );

        this.markerId = markerId;
        markedCount = 0;
    }

    /** {@inheritDoc } */
    @Override
    public void clear() {
        super.clear();
        markedCount = 0;
    }

    /** Реализация установки или снятия пометки на одну запись. */
    protected void doMark( ITreeDataSetItem<P> item, boolean mark )
    {
        if( !isSupportMark() )
            return;

        if( item == null || item.getDataSet() != this || item.getValue() == null || !containsItem(item) )
            return;

        if (!(item.getValue() instanceof IMarkable))
            throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Tree item value does not implement IMarkable. Class: " + item.getValue().getClass().getName() );

        final IMarkable markable = (IMarkable) item.getValue();

        if( markable.isMark() == mark )
            return;

        /*
         * Если markerId ещё не создавался, снять пометку невозможно.
         * События не стреляем, локальное состояние не меняем.
         */
        if( !mark && getMarkerId() == null) {
            return;
        }

        final DataSetMarkEvent.MarkActionEnum action = mark ? MARK_ROW : UNMARK_ROW;

        try {

            fireMarkDataSetEvent(action, item, true);

            XXIDsMarkerDao.markRow( getTaskContextForUse(), mark ? getOrCreateMarkerId() : getMarkerId(), markDescriptor, markable, mark );

            markable.setMark(mark);

            if( mark) {
                markedCount++;
            }
            else if (markedCount > 0) {
                markedCount--;
            }

            fireMarkDataSetEvent(action, item, false);
        }
        catch (Throwable th) {
            throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Error on call doMark", th );
        }
    }

    /** */
    public void markItem( ITreeDataSetItem<P> item ) {
        doMark( item,  true );
    }

    /**
     * Снятие пометки записи
     */
    public void unMarkItem( ITreeDataSetItem<P> item ) {
        doMark( item, false);
    }

    /** {@inheritDoc } */
    public void markCurrentItem( ) {
        doMark( getCurrentItem(), true );
    }

    /** {@inheritDoc } */
    public void unMarkCurrentItem( ) {
        doMark( getCurrentItem(), false );
    }


    /** */
    public boolean isMarkItem( ITreeDataSetItem<P> item )
    {
        if( !isSupportMark() || item == null || item.getDataSet() != this)
            return false;

        final P value = item.getValue();

        return value instanceof IMarkable && ((IMarkable) value).isMark();
    }


    /** */
    public boolean isMarkCurrentItem( ) {
        return isMarkItem( getCurrentItem() );
    }


    /** */
    public void markAll( boolean leafOnly )
    {
        if( !isSupportMark() || isEmpty() )
            return;

        try {

            final CompositeAdapter<P, ?> ca = getCompositeAdapter();
            final List<Comparable<?>> idsList = new ArrayList<>();

            traversal(item -> {

                if( item == null || !( item.getValue() instanceof IMarkable ) )
                    return true;

                if( leafOnly && !item.isLeaf() )
                    return true;

                final P value = item.getValue();
                final IMarkable markable = (IMarkable) value;

                if( !markable.isMark() )
                {
                    final Comparable<?> id = ca.getId(value);

                    if( id == null )
                        throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Cannot mark row with null ID. Class: " + value.getClass().getName() );

                    idsList.add(id);
                }

                return true;
            });

            if( idsList.isEmpty() )
                return;

            fireMarkDataSetEvent( MARK_ALL, true, leafOnly );

            XXIDsMarkerDao.markAll( getTaskContextForUse(), getOrCreateMarkerId(), markDescriptor, idsList );

            super.executeQuery();

            fireMarkDataSetEvent( MARK_ALL, false, leafOnly);
        }
        catch (Throwable th) {
            throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Error on markAll", th );
        }
    }

    public void markAll( )
    {
        markAll(false);
    }


    /** */
    public void unMarkAll() {

        if( !isSupportMark() )
            return;

        if( getMarkerId() == null )
            return;

        try {

            fireMarkDataSetEvent( UNMARK_ALL, true, false );

            XXIDsMarkerDao.unMarkAllRow( getTaskContextForUse(), getMarkerId(), markDescriptor );

            /*
             * recalcMarkedCount() сработает на EXECUTE after.
             */
            super.executeQuery();

            fireMarkDataSetEvent( UNMARK_ALL, false, false );
        }
        catch (Throwable th) {
            throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Error on unMarkAll", th );
        }
    }


    /** */
    public boolean hasMarkedItems() {

        if( isSupportMark() ) {
            return markedCount > 0;
            /*
            return !traversal( new Function< ITreeDataSetItem< P >, Boolean >() {
                @Override
                public Boolean apply( ITreeDataSetItem<P> t ) {
                    return !(t.getValue() != null && ((IMarkable)t.getValue()).isMark());
                }
            });
           */
        }

        return false;
    }


    /** */
    public Iterator<P> getMarkedRowIterator() {

        if( !hasMarkedItems() )
            return Collections.emptyIterator();

        final List<P> markedList = new ArrayList<>(markedCount);

        traversal(item -> {

            if( item == null )
                return true;

            final P value = item.getValue();

            if( value instanceof IMarkable && ((IMarkable) value).isMark() )
                markedList.add(value);

            return true;
        });

        return markedList.iterator();
    }


    /** */
    public boolean isEnableMark() {
        return isSupportMark() && getMarkerId() != null;
    }


    /** */
    private String sql4MarkColumn;


    /** {@inheritDoc } */
    @Override
    protected String getMarkSQLPart()
    {
        if( !isSupportMark() || getMarkerId() == null )
            return null;

        if( sql4MarkColumn == null )
        {
            final String[] pkColumns   = markDescriptor.getMarkKeyColumns();
            final String markKeyColumn = "qrslt." + pkColumns[0];

            final StringBuilder sb = new StringBuilder();

            switch (markDescriptor.getMarkMode()) {
                case MRK:
                    sb.append("nvl((select 1 from dual where exists (select 1 from v_jf_mrk a$ where a$.IMRKMARKERID = :jinv_marker_id and a$.RMRKROWID = ").append(markKeyColumn).append(")),0) ");
                    break;
                case MRK_ID:
                    sb.append("nvl((select 1 from dual where exists ( select 1 from v_jf_mrk_id a$ where a$.idmarker = :jinv_marker_id and a$.idrow = ").append(markKeyColumn).append(")),0) ");
                    break;
                case MRK_U:
                    sb.append( "nvl((select 1 from dual where exists ( select 1 from v_jf_mrk_u a$ where a$.idmarker = :jinv_marker_id and a$.UROW = ").append(markKeyColumn).append(")),0) ");
                    break;
                default:
                    throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Unsupported mark mode: " + markDescriptor.getMarkMode() );
            }

            sb.append(IMarkable.MARK_COLUMN).append('\n');

            sql4MarkColumn = sb.toString();
        }

        return sql4MarkColumn;
    }


    /**
     * Первое выполнение запроса dataSet
     * загружаем авто-фильтр
     */
    protected void onBeforeFirstExecute( )
    {
        if( isEnableAutoFilter() )
        {
            String name = this.getName();
            String form = this.getProperty("form_name");

            if( U.containsNull( name, form ) )
                return;

            try {

                final XXIDsDao.FilterData filterData = XXIDsDao.loadAutoFilter(getTaskContextForUse(), form, name);

                if( filterData != null ) {
                    //autoFilter = filterData.getFilterSql();
                    //setFilter( filterData.getFilterSql(), false, false, true );
                    predicates.put( -1, new PredicateItem(filterData.getFilterSql(), PredicateTypeEnum.AUTO_FILTER));
                    setProperty("auto_filter_name", filterData.getFilterName());
                }
            } catch(Throwable th ) {
                throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Error on load and init autoFilter", th );
            }
        }
    }

}
