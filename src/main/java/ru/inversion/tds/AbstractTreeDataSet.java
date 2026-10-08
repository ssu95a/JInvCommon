package ru.inversion.tds;

import ru.inversion.dataset.*;
import ru.inversion.utils.*;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static ru.inversion.dataset.DataSetEvent.DataSetEventType.*;
import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.*;
import static ru.inversion.meta.EntityMetadataFactory.getEntityMetaData;

/** */
public abstract class AbstractTreeDataSet<P> implements ITreeDataSet<P> {

    private static ITreeDataSetItemFactory g_defaultItemFactory;

    public static ITreeDataSetItemFactory getDefaultItemFactory() {
        return g_defaultItemFactory;
    }
    public static void setDefaultItemFactory(ITreeDataSetItemFactory factory) {
        g_defaultItemFactory = factory;
    }

    final static protected List<Comparable<?>> ROOT_NULL_VALUE = Collections.singletonList(null);
    final static protected BiFunction<Comparable<?>,Comparable<?>,Boolean> RCNull  = ( id, parentId ) -> parentId == null;

    /**
     * Тип записи содержащихся в TreeDataSet.
     * @see #setRowClass
     * @see #getRowClass
     */
   private	Class<? super P> rowClass;

   /** */
   private transient volatile CompositeAdapter<P, ?> compositeAdapter;

   /** Всего узлов в TreeDataSet */
   protected transient int totalItemCount = 0;

   /** Из них листовых  */
   protected transient int leafItemCount = 0;

   /**
    * Набор параметров для TreeDataSet установленных из вне.
    * @see #setCallbackParameters
    */
   private IParameters callbackParameters;

   /**
    * Параметры хранящиеся внутри TreeDataSet
    */
   protected ParametersValues dataSetParameters;

   /**
    * Наименование TreeDataSet.
    */
   private String name;

   /**
    * Дополнительные пользовательские свойства TreeDataSet.
    */
   private Map<String,Object> properties;

   /**
    * Список корневых элементов
    */
   final protected List<ITreeDataSetItem<P>> rootList;


   /** Текущий элемент внутри TreeDataSet */
   transient private ITreeDataSetItem<P> currentItem;

    /**
     * Коллекция слушателей событий связанных с элементами ITreeDataSetItem
     * @see ITreeDataSetRowsListener
     * @see TreeDataSetRowsEvent
     */
   final private IListenerManEvent<ITreeDataSetRowsListener<P>, TreeDataSetRowsEvent<P>> dataSetRowsListeners =
            ListenerManFactory.<ITreeDataSetRowsListener<P>, TreeDataSetRowsEvent<P>>createListenerManEvent( ITreeDataSetRowsListener::rowsOperation);

    /**
     * Коллекция слушателей событий связанных с событиями навигации TreeDataSet
     * @see ITreeDataSetNavigationListener
     * @see TreeDataSetNavigationEvent
     */
    final private IListenerManEvent<ITreeDataSetNavigationListener<P>,TreeDataSetNavigationEvent<P>> navigationListeners =
            ListenerManFactory.createListenerManEvent( ITreeDataSetNavigationListener::navigated );

    /**
     * <h6>Коллекция слушателей событий самого TreeDataSet.</h6>
     * @see ITreeDataSetListener
     * @see TreeDataSetEvent
     */
    final private IListenerManEvent<ITreeDataSetListener<P>, TreeDataSetEvent<P>> dataSetListeners =
            ListenerManFactory.createListenerManEvent( ITreeDataSetListener::dataSetChanged );

    /**
     * <h56>Коллекция слушателей событий связанных с элементами ITreeDataSetItem</h6>
     * @see ITreeDataSetItemListener
     * @see TreeDataSetItemEvent
     */
    final private IListenerManEvent<ITreeDataSetItemListener<P>, TreeDataSetItemEvent<P>> itemListeners =
            ListenerManFactory.createListenerManEvent( ITreeDataSetItemListener::itemChanged);

   /** */
   public AbstractTreeDataSet( ) {
        this(null);
    }

   /** */
   public AbstractTreeDataSet( Class<? super P> rowClass ) {

      this.rowClass = rowClass;
      this.rootList = AbstractDataSetBase.getDataSetListFactory().<ITreeDataSetItem<P>>createDataSetList();

      addRowsListener(new ITreeDataSetRowsListener< P >() {
         @Override
         public void rowsOperation( TreeDataSetRowsEvent<P> event ) {

            if( event.isAfter() )
             {
                 if( event.getItems() == null )
                 {
                     totalItemCount = 0;
                     leafItemCount  = 0;
                 }
                 else if( event.getItems().size() == 1 && event.getItems().get(0).isLeaf() )
                 {
                     if( event.getItemOperation() == INSERT ) {
                         totalItemCount ++;
                         leafItemCount++;
                     }
                     else if ( event.getItemOperation() == DELETE ) {
                         totalItemCount --;
                         leafItemCount  --;
                     }
                 }
                 else
                     computeTotalItemCount();
             }
         }
      });
    }

    /** */
    @Override
    public Class<P> getRowClass() {
        return (Class<P>)rowClass;
    }

   /** */
   protected CompositeAdapter<P, ?> getCompositeAdapter() {

      CompositeAdapter<P, ?> adapter = compositeAdapter;

      if( adapter == null )
      {
         synchronized (this) {
            adapter = compositeAdapter;
            if( adapter == null ) {
                adapter = new CompositeAdapter<>(getEntityMetaData(getRowClass()));
                compositeAdapter = adapter;
            }
         }
      }

      return adapter;
   }

    /** helper */
    protected List<ITreeDataSetItem<P>> rootList() {
        return rootList;
    }

   /**
    * Устанавливает тип записи для TreeDataSet.
    * <p>
    * @param rowClass
    *        класс записи
    *
    * @throws IllegalStateException
    *         если происходит изменить тип записи TreeDataSet, с уже загруженными записями
    */
    public void setRowClass( Class<? extends P> rowClass ) {

        if( this.rowClass != rowClass ) {

            if( !isEmpty() )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Can't change the type of record, because it is already loaded other type of entry" );

            this.rowClass = (Class)rowClass;

            this.compositeAdapter = null;
        }
    }

    /** */
    @Override
    public String getName()  {
        return name;
    }

    /** */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public <T> T getProperty(String propertyName) {
        return properties == null ? null : (T)properties.get(propertyName);
    }

    /** {@inheritDoc} */
    @Override
    public void setProperty(String propertyName, Object value) {
        if( properties == null )
            properties = new HashMap<>();
        properties.put( propertyName, value );
    }


   /** */
   protected Object getCurrentItemId() {

      ITreeDataSetItem<P> current = getCurrentItem();

      if( current == null || current.getValue() == null )
          return null;

      return getCompositeAdapter().getId( current.getValue() );
   }


   /** */
   protected boolean restoreCurrentItemById( Object id )
   {
      if( id == null || isEmpty() )
          return false;

      final CompositeAdapter<P, ?> adapter = getCompositeAdapter();

      return findItem(
         new TreeDataSetSearchParam<>( value -> value != null && U.equals(id, adapter.getId(value)), false, true, false )
      );
   }

    /**
        +------------------------------+
        | РАБОТА С ПАРАМЕТРАМИ DATASET |
        +------------------------------+
    */

    /** {@inheritDoc} */
    @Override
    public void setCallbackParameters(IParameters parameters) {
        this.callbackParameters = parameters;
    }

    /** {@inheritDoc}  */
    @Override
    public IParameters getCallbackParameters() {
        return  callbackParameters;
    }

   /**
    * Версия метода setCallbackParameters в стиле "Fluent interface".
    * <p>
    * @param parameters
    *        внешний набор параметров для TreeDataSet, {@code null} для очистки внешнего набора внутри TreeDataSet
    *
    * @return this
    * @see #setCallbackParameters
    */
    public AbstractTreeDataSet<P> callbackParameters(IParameters parameters ) {
        setCallbackParameters(parameters);
        return this;
    }

    /** */
    public void setDataSetParameters( ParametersValues pv ) {
        this.dataSetParameters = pv;
    }

    /** */
    public ParametersValues getDataSetParameters( ) {
        return this.dataSetParameters;
    }

    /** */
    public void setParameter( String name, Object value ) {
        if( dataSetParameters == null )
            dataSetParameters = new ParametersValues();
        this.dataSetParameters.set(name, value);
    }

    /** */
    public void setParameter( int index,  Object value ) {
        if( dataSetParameters == null )
            dataSetParameters = new ParametersValues();
        this.dataSetParameters.set(index, value);
    }

    /** */
    @Override
    public Object getParameter( String parameterName ) {
        return dataSetParameters == null ? null : dataSetParameters.getParameter(parameterName);
    }

    /** */
    @Override
    public Object getParameter( int parameterIndex ) {
        return dataSetParameters == null ? null : dataSetParameters.getParameter(parameterIndex);
    }

    /** */
    protected ITreeDataSetItemFactory getItemFactory()
    {
        return getDefaultItemFactory();
    }

    /**
         +---------------------------------------+
         | РАБОТА С КОРНЕВЫМИ УЗЛАМИ TreeDATASET |
         +---------------------------------------+
     */

    /** */
    @Override
    public List<ITreeDataSetItem<P>> getRootList() {
       return Collections.unmodifiableList(rootList);
    }

    /** */
    @Override
    public ITreeDataSetItem<P> getRoot() {

        if( rootList.isEmpty() )
            return null;

        if( rootList.size() == 1 )
            return rootList.get(0);

        throw new IllegalStateException( Tags.PRODUCT_LABEL + "Many root items" );
    }

    /** */
    public void createRoot( P ... values )
    {
        createRoot( Arrays.asList(values) );
    }

    /** */
    public void createRoot( Collection<P> values )
    {
        if( !rootList.isEmpty() )
             clear();

        final ITreeDataSetItemFactory itemFactory = getItemFactory();

        final List<ITreeDataSetItem<P>> items = new ArrayList<>();
        values.forEach( (p)->items.add( itemFactory.createRootItem(this,p) ) );

        dataSetRowsListeners.fire( new TreeDataSetRowsEvent<>( this, true,  INSERT, items, 0 ) );

        rootList.addAll( items );

        dataSetRowsListeners.fire( new TreeDataSetRowsEvent<>( this, false, INSERT, rootList, 0 ) );
    }


   /** */
   public ITreeDataSetItem<P> insertRoots( Collection<P> values, IDataSet.InsertRowModeEnum insertMode, boolean doSetToCurrent )
   {
      if( values == null || values.isEmpty() )
          return null;

      final ITreeDataSet<P> _this = this;

      final List<ITreeDataSetItem<P>> newItems = new ArrayList<>(values.size());
      values.forEach((p) -> newItems.add(getItemFactory().createRootItem(_this, p)));

      int position = -1;
      boolean addToEnd = false;

      if( rootList().isEmpty() )
      {
         position = 0;
      }
      else
      {
         final IDataSet.InsertRowModeEnum effectiveInsertMode = insertMode == null ? IDataSet.InsertRowModeEnum.LAST : insertMode;

         switch( effectiveInsertMode )
         {
            case FIRST:
               position = 0;
               break;

            case LAST:
               addToEnd = true;
               break;

            case BEFORE_CURRENT:
            {
               final ITreeDataSetItem<P> ci = getCurrentItem();
               position = ci == null ? -1 : rootList().indexOf(ci);

               if( position == -1 )
                   addToEnd = true;

               break;
            }

            case AFTER_CURRENT:
            {
               final ITreeDataSetItem<P> ci = getCurrentItem();
               int currentIndex = ci == null ? -1 : rootList().indexOf(ci);

               if( currentIndex == -1 || currentIndex >= rootList().size() - 1 )
                   addToEnd = true;
               else
                  position = currentIndex + 1;

               break;
            }
            default:
               addToEnd = true;
               break;
         }
      }

      if( addToEnd )
          position = rootList().size();

      fireBeforeRowsEvent( INSERT, newItems, position );

      if( addToEnd )
          rootList().addAll( newItems );
      else
          rootList().addAll( position, newItems );

      fireAfterRowsEvent( INSERT, newItems, position );

      if( doSetToCurrent )
          setCurrentItem( newItems.get(0) );

      return newItems.get(0);
   }

    /** */
    public ITreeDataSetItem<P> insertRoot( P value, IDataSet.InsertRowModeEnum insertMode, boolean doSetToCurrent )
    {
        return insertRoots( Collections.singletonList(value), insertMode, doSetToCurrent );
    }

    /** */
    public ITreeDataSetItem<P> insertRoot( P value ) {

        final ITreeDataSetItemFactory itemFactory = getItemFactory();
        final ITreeDataSetItem<P> item = itemFactory.createRootItem( this, value);

        int index = rootList.size();

        if(!dataSetRowsListeners.isEmpty() )
            dataSetRowsListeners.fire( new TreeDataSetRowsEvent<>( this, true , INSERT, Collections.singletonList(item), index ) );

        rootList.add( item );

        if(!dataSetRowsListeners.isEmpty() )
            dataSetRowsListeners.fire( new TreeDataSetRowsEvent<>( this, false, INSERT, Collections.singletonList(item), index ) );

        return item;
    }

   /** */
   protected boolean containsItem(ITreeDataSetItem<P> item)
   {
      if( item == null )
          return false;

      for( ITreeDataSetItem<P> root : rootList() )
      {
         if( containsItem( root, item ) )
             return true;
      }

      return false;
   }

   /** */
   private boolean containsItem (
        ITreeDataSetItem<P> root,
        ITreeDataSetItem<P> item
   )
   {
      if( root == null || item == null )
          return false;

      if( root == item )
          return true;

      if( root.isLeaf() )
          return false;

      for (ITreeDataSetItem<P> child : root.getChildrenList())
      {
         if( containsItem(child, item) )
             return true;
      }

      return false;
   }

   /** */
   public boolean removeRootItem( ITreeDataSetItem<P> item )
   {
      if( item == null || item.getDataSet() != this || !item.isRoot() )
          return false;

      final int removedIndex = rootList().indexOf(item);

      if( removedIndex == -1 )
          return false;

      final ITreeDataSetItem<P> oldCurrent = getCurrentItem();

      final boolean currentRemoved = containsItem( item, oldCurrent);

      dataSetRowsListeners.fire( new TreeDataSetRowsEvent<>( this, true, DELETE, Collections.singletonList(item), removedIndex ) );

      if( removedIndex >= rootList().size() || rootList().get(removedIndex) != item )
          throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Root list was modified during DELETE before event" );

      rootList( ).remove( removedIndex );

      if( currentRemoved )
      {
         ITreeDataSetItem<P> newCurrent = null;

         if( !rootList().isEmpty() )
         {
            int newCurrentIndex = removedIndex;

            if( newCurrentIndex >= rootList().size() )
                newCurrentIndex = rootList().size() - 1;

            newCurrent = rootList().get(newCurrentIndex);
         }

         if( newCurrent != null )
            setCurrentItem(newCurrent);
         else
            clearCurrentItem();
      }

      dataSetRowsListeners.fire( new TreeDataSetRowsEvent<>( this, false, DELETE, Collections.singletonList(item), removedIndex ) );

      return true;
   }

    /**
         +------------------------------------+
         | РАБОТА С ТЕКУЩИМ УЗЛОМ TreeDataSet |
         +------------------------------------+
     */

   /**
    * Удаление текущей записи из TreeDataSet.
    * <p>
    * Метод перемещает позицию с удаляемой записи
    * на следующий доступный элемент.
    *
    * @return удалённый элемент или {@code null},
    *         если текущего элемента нет либо удаление не выполнено
    */
   @Override
   public ITreeDataSetItem<P> removeCurrentItem()
   {
      final ITreeDataSetItem<P> item = getCurrentItem();

      if( item == null )
          return null;

      final boolean removed;

      if( item.isRoot() )
          removed = removeRootItem(item);
      else
          removed = item.remove();

      return removed ? item : null;
   }


    /** */
    @Override
   public ITreeDataSetItem<P> getCurrentItem( ) {
        return currentItem;
    }


   /** helper для событий смены текущего элемента */
   private boolean changeCurrentItem(ITreeDataSetItem<P> item)
   {
      if( item != null )
      {
         if( item.getDataSet() != this )
             throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Tree item belongs to another TreeDataSet" );

         if( !containsItem(item) )
             throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Tree item is not contained in this TreeDataSet" );
      }

      if( item == currentItem )
          return false;

      final ITreeDataSetItem<P> oldItem = currentItem;

      currentItem = item;

      fireNavigationEvent(oldItem, currentItem);

      return true;
   }

   /** */
   protected boolean clearCurrentItem() {
      return changeCurrentItem(null);
   }

   /** */
   @Override
   public boolean setCurrentItem( ITreeDataSetItem<P> item ) {

      if( item == null )
         return false;

      return changeCurrentItem(item);
   }


   /** */
   @Override
   public boolean findItem( Predicate<P> predicate )
   {
      return findItem( new TreeDataSetSearchParam<>( predicate, false, true, false ) );
   }


   /** */
   private boolean matchesSearch( ITreeDataSetItem<P> item, TreeDataSetSearchParam<P> searchParam )
   {
      if( item == null )
          return false;

      if( searchParam.inLeafOnly() && !item.isLeaf() )
          return false;

      return searchParam.predicate().test( item.getValue() );
   }

   /** */
   public boolean findItem( TreeDataSetSearchParam<P> searchParam )
   {
      if( searchParam == null || searchParam.predicate() == null || isEmpty() )
          return false;

      final List<ITreeDataSetItem<P>> items = toPreOrderList( );

      if( items.isEmpty() )
          return false;

      int currentIndex = -1;
      int startIndex   = 0;

      if( searchParam.fromCurrent() )
      {
         ITreeDataSetItem<P> current = getCurrentItem();

         if( current != null)
         {
            currentIndex = items.indexOf(current);

            if( currentIndex >= 0 )
                startIndex = currentIndex + 1;
         }
      }

      if( searchParam.fromCurrent() && searchParam.wrapAround() )
      {
         for( int offset = 0; offset < items.size(); offset++ )
         {
            int index = (startIndex + offset) % items.size();

            if( index == currentIndex )
                continue;

            ITreeDataSetItem<P> item = items.get(index);

            if( matchesSearch(item, searchParam) )
                return item.setItemCurrent();

         }

         return false;
      }

      for( int i = startIndex; i < items.size(); i++ )
      {
         ITreeDataSetItem<P> item = items.get(i);

         if( matchesSearch(item, searchParam) )
            return item.setItemCurrent();
      }

      return false;
   }

    /**
        +------------------------------------+
        | РАБОТА СО СЛУШАТЕЛЯМИ TreeDataSet  |
        +------------------------------------+
     */

    /** */
    public void addRowsListener(ITreeDataSetRowsListener<P> l ){
        dataSetRowsListeners.addListener(l);
    }
    /** */
    public void removeRowsListener(ITreeDataSetRowsListener<P> l ){
        dataSetRowsListeners.removeListener(l);
    }
    /** */
    public void fireRowsEvent(TreeDataSetRowsEvent<P> event) {
        if(!dataSetRowsListeners.isEmpty() )
            dataSetRowsListeners.fire(event);
    }
    protected void fireBeforeRowsEvent(DataSetRowEvent.RowOperationEnum rowOperation, List<ITreeDataSetItem<P>> items, int rowIndex ) {
        fireRowsEvent( new TreeDataSetRowsEvent<>( this, true, rowOperation, items, rowIndex ) );
    }
    protected void fireAfterRowsEvent(DataSetRowEvent.RowOperationEnum rowOperation, List<ITreeDataSetItem<P>> items, int rowIndex ) {
        fireRowsEvent( new TreeDataSetRowsEvent<>( this,false, rowOperation, items, rowIndex ) );
    }


    /**
     * Установить слушателя связанных с событием навигации внутри TreeDataSet.
     * События навигации формируются в момент перемещения позиции "текущей записи".
     * см. {@link TreeDataSetNavigationEvent}
     * <p>
     * @param navigationListener
     *        слушатель событий навигации
     *
     * @see ITreeDataSetNavigationListener
     * @see TreeDataSetNavigationEvent
     */
    public void addNavigationListener( ITreeDataSetNavigationListener<P> navigationListener )
    {
        navigationListeners.addListener(navigationListener);
    }

   /**
    * Удаление слушателя связанных с событием навигации добавленного методом {@code addNavigationListener}.
    * <p>
    * @param navigationListener
    *        слушатель ранее добавленный методом {@code addNavigationListener}
    *
    * @see #addNavigationListener
    */
    public void removeNavigationListener( ITreeDataSetNavigationListener<P> navigationListener )
    {
        navigationListeners.removeListener(navigationListener);
    }

    /** */
    protected void fireNavigationEvent( ITreeDataSetItem<P> oldItem, ITreeDataSetItem<P> newItem )
    {
        navigationListeners.fire( new TreeDataSetNavigationEvent<>( this, oldItem, newItem) );
    }

    /** {@inheritDoc} */
    @Override
    public void addDataSetListener( ITreeDataSetListener<P> dataSetListener) {
        dataSetListeners.addListener(dataSetListener);
    }

    /** {@inheritDoc} */
    @Override
    public void removeDataSetListener(ITreeDataSetListener<P> dataSetListener) {
        dataSetListeners.removeListener(dataSetListener);
    }

	/**
     * <h6>Формирование события связанного с поведением TreeDataSet.</h6>
     * @param eventType
     *        тип событие TreeDataSet см. {@link DataSetEvent.DataSetEventType}
     * @param before
     *        признак запуска
     *        {@code true } - перед событием
     *        {@code false} - после
     *
     * @see DataSetEvent
     * @see ITreeDataSetListener
     */
    protected void fireDataSetEvent( DataSetEvent.DataSetEventType eventType, boolean before ) {
        dataSetListeners.fire( new TreeDataSetEvent<>( this, eventType, before ) );
    }

    /** {@inheritDoc} */
    @Override
    public void addItemListener(ITreeDataSetItemListener<P> dataSetItemListener) {
        itemListeners.addListener(dataSetItemListener);
    }

    /** {@inheritDoc} */
    @Override
    public void removeItemListener(ITreeDataSetItemListener<P> dataSetItemListener) {
        itemListeners.removeListener(dataSetItemListener);
    }

    /** */
    @Override
    public void fireItemEvent(TreeDataSetItemEvent<P> event) {
        itemListeners.fire(event);
    }


   /**
    * <h6>Очищает все элементы внутри TreeDataSet.</h6>
    * <p>
    * Удаляются все записи из TreeDataSet.
    * Текущий элемент сбрасывается в {@code null}.
    *
    * В процессе очистки формируется событие
    * {@link TreeDataSetRowsEvent} с операцией {@code DELETE}.
    *
    * @see TreeDataSetRowsEvent
    */
   @Override
   public void clear()
   {
      final boolean hadRows = !rootList().isEmpty();
      final ITreeDataSetItem<P> oldCurrent = getCurrentItem();

      if( hadRows )
      {
         fireRowsEvent( new TreeDataSetRowsEvent<>( this, true, DELETE, rootList(), 0 ) );
         rootList().clear();
      }

      totalItemCount = 0;
      leafItemCount  = 0;
      currentItem    = null;

      if( oldCurrent != null )
          fireNavigationEvent( oldCurrent, null );

      if( hadRows )
          fireRowsEvent( new TreeDataSetRowsEvent<>( this, false, DELETE, null, 0 ) );
   }


   /** */
   public boolean isEmpty()
    {
        return rootList.isEmpty();
    }


   /** */
   protected List<ITreeDataSetItem<P>> toPreOrderList() {

      List<ITreeDataSetItem<P>> items = new ArrayList<>();

      traversal(item -> {
         items.add(item);
         return true;
      });

      return items;
   }

   /** */
   private boolean traversalHelper( ITreeDataSetItem<P> item, Function<ITreeDataSetItem<P>, Boolean> visitor )
   {
      if( item == null )
          return true;

      if( !visitor.apply(item))
          return false;

      if( item.isLeaf() )
          return true;

      for (ITreeDataSetItem<P> child : item.getChildrenList())
      {
         if( !traversalHelper(child, visitor) )
             return false;
      }

      return true;
   }


    /** Обход дерева */
    public boolean traversal( Function<ITreeDataSetItem<P>, Boolean> visitor )
    {
       if( visitor == null )
           return false;

       if( isEmpty() )
           return true;

       for( ITreeDataSetItem<P> root : getRootList() )
       {
          if( !traversalHelper(root, visitor) )
              return false;
       }

       return true;
    }

    /** */
    protected void doClose()
    { }


    /** */
    @Override
    public void close() {

       fireDataSetEvent(CLOSE, true);

       doClose();
       clear();

       fireDataSetEvent(CLOSE, false);
    }


    /** */
    @Override
    public int getTotalItemCount() {
        return totalItemCount;
    }

    /** */
    public int getLeafItemCount() {
        return leafItemCount;
    }

    /** */
    public void computeTotalItemCount()
    {
        totalItemCount = rootList().size();
        leafItemCount  = 0; //(int)rootList().stream().filter( (t)->t.isLeaf() ).count();

        traversal(new Function< ITreeDataSetItem< P >, Boolean >() {
            @Override
            public Boolean apply( ITreeDataSetItem< P > item ) {
                if( !item.isLeaf() )
                    totalItemCount += item.getChildCount();
                else
                    leafItemCount ++;
                return true;
            }
        });
    }

    /** */
    public void dump( Map<String,Object> d ) {

        if( d == null )
            return;

        d.put("ds.rowClass", 	   getRowClass() != null ? getRowClass().getName() : "<null>"  );
        d.put("ds.name",     	   S.nvl ( getName() ) );
        d.put("ds.loadedRowCount", this.getTotalItemCount() );

        if( properties != null ) {
            final StringBuilder sb = new StringBuilder();
            properties.forEach((String t, Object u) -> {
                sb.append(t).append(" = ").append( S.nvl(u) ).append('\n');
            });
            d.put("ds.properties", sb.toString() );
        }

        if( dataSetParameters != null )
            dataSetParameters.dump(d);

        if( getCurrentItem() != null )
            d.put("ts.item.path", getCurrentItem().getPath().toString() );

    }

    /** */
    protected void populate ( Iterator<P> iter, BiFunction<Comparable<?>, Comparable<?>, Boolean> rootCheck ) throws TreeDataSetException
    {
       Checks.Require.objects( iter, "iter", rootCheck, "rootCheck" );

       final CompositeAdapter<P, ?> adapter = getCompositeAdapter();

       final List<P> rows = new ArrayList<>();

       for( P p : U.iterable(iter) )
       {
          if( p != null )
              rows.add(p);
       }

       if( rows.isEmpty() )
           return;

       /*
        * LinkedHashMap: - сохраняем порядок ResultSet / Iterator.
        */
       final Map<Comparable<?>, P> pojoById = new LinkedHashMap<>();
       final Map<Comparable<?>, Comparable<?>> parentById = new LinkedHashMap<>();
       final Map<Comparable<?>, List<Comparable<?>>> childrenByParent = new LinkedHashMap<>();
       final List<Comparable<?>> rootIds = new ArrayList<>();

       /*
        * Первый проход: собираем ID, parentId, ловим null ID и duplicate ID.
        */
       for( P p : rows )
       {
          final Comparable<?> id = adapter.getId(p);
          final Comparable<?> parentId = adapter.getParentId(p);

          if( id == null )
              throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Tree item ID is null. Class: " + getRowClass().getName() + ", item: " + p );

          if( pojoById.containsKey(id) )
              throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Duplicate tree item ID '" + id + "'. Class: " + getRowClass().getName() );

          pojoById.put(id, p);
          parentById.put(id, parentId);
       }

       /*
        * parent -> children.
        */
       for( P p : rows )
       {
          final Comparable<?> id = adapter.getId(p);
          final Comparable<?> parentId = adapter.getParentId(p);

          if( isRoot( id, parentId, rootCheck ) )
          {
             rootIds.add(id);
             continue;
          }

          if( parentId == null )
          {
             rootIds.add(id);
             continue;
          }

          if( id.equals(parentId) )
              throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Tree item '" + id + "' refers to itself as parent. Class: " + getRowClass().getName() );

          if (!pojoById.containsKey(parentId)) {
             /*
              * parent может быть отфильтрован SQL-ом или не загружен.
              */
             rootIds.add(id);
             continue;
          }

          childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
       }

       /*
        * проверяем циклы.
        */
       detectCycles(parentById, rootCheck);

       final List<ITreeDataSetItem<P>> newRoots = new ArrayList<>(rootIds.size());

       /*
        * Создаём дерево.
        * Порядок rootIds и childrenByParent соответствует порядку входных rows.
        */
       for( Comparable<?> rootId : rootIds )
       {
          final P rootPojo = pojoById.get(rootId);
          final ITreeDataSetItem<P> rootItem = getItemFactory().createRootItem(this, rootPojo);

          newRoots.add(rootItem);

          createChildren( rootId, rootItem, pojoById, childrenByParent );
       }

       final int insertPosition = rootList.size();

       fireRowsEvent (
            new TreeDataSetRowsEvent<>(this, true, INSERT, newRoots, insertPosition)
       );

       rootList.addAll(newRoots);

       fireRowsEvent(
            new TreeDataSetRowsEvent<>(this, false, INSERT, newRoots, insertPosition)
       );
    }


   /** */
   private boolean isRoot( Comparable<?> id, Comparable<?> parentId, BiFunction<Comparable<?>, Comparable<?>, Boolean> rootCheck )
   {
      return Boolean.TRUE.equals( rootCheck.apply(id, parentId) );
   }


   /** */
   private void createChildren( Comparable<?> parentId, ITreeDataSetItem<P> parentItem, Map<Comparable<?>, P> pojoById, Map<Comparable<?>, List<Comparable<?>>> childrenByParent )
   {
      final List<Comparable<?>> childIds = childrenByParent.get(parentId);

      if( childIds == null || childIds.isEmpty() )
          return;

      for( Comparable<?> childId : childIds )
      {
         final P childPojo = pojoById.get(childId);
         final ITreeDataSetItem<P> childItem = getItemFactory().createItem(parentItem, childPojo);

         createChildren( childId, childItem, pojoById, childrenByParent );
      }
   }


   /** */
   private void detectCycles( Map<Comparable<?>, Comparable<?>> parentById, BiFunction<Comparable<?>, Comparable<?>, Boolean> rootCheck ) throws TreeDataSetException
   {
      final Set<Comparable<?>> visited = new HashSet<>();
      final Set<Comparable<?>> visiting = new HashSet<>();

      for( Comparable<?> id : parentById.keySet() )
      {
         detectCycle( id, parentById, rootCheck, visiting, visited );
      }
   }


   /** */
   private void detectCycle( Comparable<?> id, Map<Comparable<?>, Comparable<?>> parentById, BiFunction<Comparable<?>, Comparable<?>, Boolean> rootCheck, Set<Comparable<?>> visiting, Set<Comparable<?>> visited )
      throws TreeDataSetException
   {
      if( visited.contains(id) )
         return;

      if( visiting.contains(id) )
         throw new TreeDataSetException( Tags.PRODUCT_LABEL + "Cycle detected in tree data. Item ID: " + id + ". Class: " + getRowClass().getName() );

      visiting.add(id);

      final Comparable<?> parentId = parentById.get(id);

      if (parentId != null && !isRoot(id, parentId, rootCheck) )
      {

         if( id.equals(parentId) ) {
            throw new TreeDataSetException (
               Tags.PRODUCT_LABEL + "Tree item '" + id + "' refers to itself as parent. Class: " + getRowClass().getName() );
         }

         if( parentById.containsKey(parentId) ) {
            detectCycle( parentId, parentById, rootCheck, visiting, visited );
         }
      }

      visiting.remove(id);
      visited.add(id);
   }
}
