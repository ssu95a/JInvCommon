package ru.inversion.tds;

import ru.inversion.dataset.IDSBase;
import ru.inversion.dataset.IParameters;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** */
public interface ITreeDataSet<P> extends IDSBase<P> {

    /**
     *  Список корневых элементов
     */
    List< ITreeDataSetItem<P> > getRootList();

   /**
    * Получить корневой элемент если он один в TreeDataSet
    *
    * @throws {@link IllegalStateException} если их там несколько, если нет элементов то {@code null}
    */
    ITreeDataSetItem<P> getRoot();

   /**
    * Удалить корневой узел
    * @param item
    *        Узел для удаления
    * @return Если узел был удален то {@code True} иначе {@code False}
    */
    boolean removeRootItem( ITreeDataSetItem<P> item);

    /**
     * Получить текущий узел, установленный внутри TreeDataSet
     * @return Текущий узел или {@code null} если нет текущего
     */
    ITreeDataSetItem<P> getCurrentItem();

    /**
     * Установить текущий узел внутри TreeDataSet

     * @param item
     *        Узел для установки
     *
     * @return Если узел был установлен то {@code True} иначе {@code False}
     */
    boolean setCurrentItem( ITreeDataSetItem<P> item );

    /**
     * Удалить текущий узел из TreeDataSet
     * @return Если удаленный узел или {@code null} если не было установленного текущего узла
     */
    ITreeDataSetItem<P> removeCurrentItem( );

    /** */
    void setCallbackParameters( IParameters parameters);

    /** */
    IParameters getCallbackParameters();

    /** */
    void executeQuery( ) throws TreeDataSetException;

    /** */
    void clear( );

    /** */
    boolean isEmpty();

    /** */
    int getTotalItemCount();

    /** */
    boolean findItem( Predicate<P> predicate );
    boolean findItem( TreeDataSetSearchParam<P> searchParam );

    boolean traversal( Function<ITreeDataSetItem<P>,Boolean> visitor );

    /** */
    void addDataSetListener(ITreeDataSetListener<P> dataSetListener);
    /** */
    void removeDataSetListener(ITreeDataSetListener<P> dataSetListener);

    /** */
    void addNavigationListener(ITreeDataSetNavigationListener<P> dataSetListener);
    /** */
    void removeNavigationListener(ITreeDataSetNavigationListener<P> dataSetListener);

    /** */
    void addRowsListener(ITreeDataSetRowsListener<P> l );
    /** */
    void removeRowsListener(ITreeDataSetRowsListener<P> l );
    /** */
    void fireRowsEvent(TreeDataSetRowsEvent<P> event );

    /** */
    void addItemListener(ITreeDataSetItemListener<P> dataSetListener);
    /** */
    void removeItemListener(ITreeDataSetItemListener<P> dataSetListener);
    /** */
    void fireItemEvent(TreeDataSetItemEvent<P> event );


}
