package ru.inversion.tds;

import ru.inversion.dataset.DataSetRowEvent;
import ru.inversion.dataset.IDataSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.DELETE;
import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.INSERT;

/** */
public interface ITreeDataSetItem<P> {

    /** */
    ITreeDataSet<P> getDataSet();


    /** */
    ITreeDataSetItem<P> getParentItem();


   /**
    * Возвращает неизменяемое представление списка дочерних элементов.
    * <p>
    * Может возвращать {@code null}, если дочерние элементы отсутствуют.
    * Изменение структуры выполняется через методы {@link #addChild},
    * {@link #addChildrenAt} и {@link #removeChildren}.
    */
    List<ITreeDataSetItem<P>> getChildrenList();


    /** */
    P getValue();


    /** */
    void setValue(P value);


    /** */
    void executeQuery();


    /** */
    ITreeDataSetItem<P> newChildItem(P value);


    /** */
    void addChild(ITreeDataSetItem<P> child);

    /** */
    void addChildrenAt(
        int position,
        List<ITreeDataSetItem<P>> newItems
    );

    /** */
    void removeChildren(Collection<ITreeDataSetItem<P>> items);

    /** */
    default boolean isRoot() {
        return getParentItem() == null;
    }

    /** */
    default boolean isLeaf() {
        List<ITreeDataSetItem<P>> children = getChildrenList();
        return children == null || children.isEmpty();
    }

    /** */
    default int getChildCount() {
        List<ITreeDataSetItem<P>> children = getChildrenList();
        return children == null ? 0 : children.size();
    }

    /** */
    default ITreeDataSetItem<P> getRootItem() {
        ITreeDataSetItem<P> parent = getParentItem();
        return parent == null ? this : parent.getRootItem();
    }

    /** */
    default ITreeDataSetItem<P> getChildAt(int childIndex) {

        List<ITreeDataSetItem<P>> children = getChildrenList();

        if (children == null || childIndex < 0 || childIndex >= children.size()) {
            return null;
        }

        return children.get(childIndex);
    }

    /** */
    default int getChildIndex(ITreeDataSetItem<P> child) {

        if (child == null || child.getParentItem() != this) {
            return -1;
        }

        List<ITreeDataSetItem<P>> children = getChildrenList();

        if (children == null || children.isEmpty()) {
            return -1;
        }

        return children.indexOf(child);
    }

    /** */
    default boolean setCurrentChildNum(int childNum) throws TreeDataSetException {

        ITreeDataSetItem<P> child = getChildAt(childNum);

        return child != null && child.setItemCurrent();
    }


    /** */
    default boolean setCurrentChild(ITreeDataSetItem<P> child) throws TreeDataSetException {

        if (child == null || child.getParentItem() != this) {
            return false;
        }

        return child.setItemCurrent();
    }


    /** */
    default boolean setItemCurrent( )
    {
       final ITreeDataSet<P> dataSet = getDataSet();
       return dataSet != null && dataSet.setCurrentItem(this);
    }


    /** */
    default boolean isCurrentItem() throws TreeDataSetException {
       final ITreeDataSet<P> dataSet = getDataSet();
       return dataSet != null && dataSet.getCurrentItem() == this;
    }


    /** */
    default void insert(P value) throws TreeDataSetException {
        insert(
                Collections.singletonList(value),
                IDataSet.InsertRowModeEnum.LAST,
                false
        );
    }

    /** */
    default void insert( List<P> newRows, IDataSet.InsertRowModeEnum insertMode, boolean doSetToCurrent )
    {
        if( newRows == null || newRows.isEmpty() )
            return;

        final List<ITreeDataSetItem<P>> newItems = new ArrayList<>(newRows.size());

        for( P value : newRows )
             newItems.add(newChildItem(value));

        final boolean wasLeaf = isLeaf();

        int position = wasLeaf ? 0 : resolveInsertPosition(insertMode);

        if (wasLeaf) {
            fireLeafEvent(true, false);
        }

        fireBeforeRowsEvent(INSERT, newItems, position);

        addChildrenAt(position, newItems);

        fireAfterRowsEvent(INSERT, newItems, position);

        if (wasLeaf) {
            fireLeafEvent(false, false);
        }

        if (doSetToCurrent && !newItems.isEmpty()) {
            newItems.get(0).setItemCurrent();
        }
    }

    /** */
    default int resolveInsertPosition(IDataSet.InsertRowModeEnum insertMode) {

        int childCount = getChildCount();

        if (childCount == 0) {
            return 0;
        }

        if (insertMode == null) {
            return childCount;
        }

        switch (insertMode) {
            case FIRST:
                return 0;

            case LAST:
                return childCount;

            case BEFORE_CURRENT: {
                ITreeDataSetItem<P> current = getDataSet().getCurrentItem();

                if (current != null && current.getParentItem() == this) {
                    int index = getChildIndex(current);
                    return index < 0 ? childCount : index;
                }

                return childCount;
            }

            case AFTER_CURRENT: {
                ITreeDataSetItem<P> current = getDataSet().getCurrentItem();

                if (current != null && current.getParentItem() == this) {
                    int index = getChildIndex(current);
                    return index < 0 ? childCount : index + 1;
                }

                return childCount;
            }

            default:
                return childCount;
        }
    }

    /** */
    default int remove(Predicate<P> predicate) throws TreeDataSetException {

        if (predicate == null) {
            return 0;
        }

        return removeItems(item -> predicate.test(item.getValue()));
    }

    /** */
    default boolean remove(ITreeDataSetItem<P> child) throws TreeDataSetException {

        if (child == null || child.getParentItem() != this) {
            return false;
        }

        return removeItems(item -> item == child) > 0;
    }


    /** */
    default boolean remove(int itemNum)
    {
        ITreeDataSetItem<P> child = getChildAt(itemNum);
        return child != null && remove(child);
    }


    /** */
    default boolean remove() throws TreeDataSetException
    {
       final ITreeDataSet<P> dataSet = getDataSet();

       if( dataSet == null )
          return false;

       if( isRoot() )
          return dataSet.removeRootItem(this);

       final ITreeDataSetItem<P> parent = getParentItem();

       return parent != null && parent.remove(this);
    }


   /** */
   default int removeItems( Predicate<ITreeDataSetItem<P>> predicate )
   {
      if( predicate == null || isLeaf() )
          return 0;

      final ITreeDataSet<P> dataSet = getDataSet();

      if( dataSet == null )
          return 0;

      final List<ITreeDataSetItem<P>> children = getChildrenList();

      if( children == null || children.isEmpty() )
          return 0;

      /*
       * Фиксируем состав и порядок детей до вызова predicate
       * и пользовательских listeners.
       */
      final List<ITreeDataSetItem<P>> childrenSnapshot = new ArrayList<>(children);

      final int totalCount = childrenSnapshot.size();

      int firstDeletedIndex = -1;
      int index = 0;

      final List<ITreeDataSetItem<P>> removed = new LinkedList<>();

      final ITreeDataSetItem<P> current = dataSet.getCurrentItem();

      boolean currentRemoved = false;

      for( ITreeDataSetItem<P> child : childrenSnapshot )
      {
         if( predicate.test(child) )
         {
            if( firstDeletedIndex == -1 )
                firstDeletedIndex = index;

            removed.add(child);

            if( !currentRemoved && child.containsItem(current) )
                 currentRemoved = true;
         }

         index++;
      }

      if( removed.isEmpty() )
          return 0;

      final boolean willBecomeLeaf =removed.size() == totalCount;

      if( willBecomeLeaf )
          fireLeafEvent(true, true);

      fireBeforeRowsEvent( DELETE, removed, firstDeletedIndex );

      /*
       * DELETE-before listener не должен менять состав
       * или порядок дочерних элементов.
       */
      final List<ITreeDataSetItem<P>> actualChildren = getChildrenList();

      if( actualChildren == null || actualChildren.size() != childrenSnapshot.size() )
          throw new TreeDataSetException( "Children list was modified during DELETE before event" );

      for( int i = 0; i < childrenSnapshot.size(); i++ )
      {
         if( actualChildren.get(i) != childrenSnapshot.get(i) )
             throw new TreeDataSetException( "Children list was modified during DELETE before event" );
      }

      removeChildren(removed);

      final int removeCount = totalCount - getChildCount();

      if( currentRemoved )
      {
         final ITreeDataSetItem<P> newCurrent = selectCurrentAfterChildrenDelete( firstDeletedIndex );
         dataSet.setCurrentItem(newCurrent);
      }

      fireAfterRowsEvent( DELETE, removed, firstDeletedIndex );

      if( willBecomeLeaf )
          fireLeafEvent(false, true);

      return removeCount;
   }


    /** */
    default ITreeDataSetItem<P> selectCurrentAfterChildrenDelete(int firstDeletedIndex) {

        if (!isLeaf()) {
            int index = firstDeletedIndex;

            if (index >= getChildCount()) {
                index = getChildCount() - 1;
            }

            if (index >= 0) {
                return getChildAt(index);
            }
        }

        return this;
    }


    /** */
    default boolean containsItem(ITreeDataSetItem<P> item)
    {
        if( item == null )
            return false;

        if( this == item )
            return true;

        if( isLeaf() )
            return false;

        for( ITreeDataSetItem<P> child : getChildrenList() ) {
            if (child.containsItem(item)) {
                return true;
            }
        }

        return false;
    }

    /** */
    default boolean findItem(Predicate<P> predicate) throws TreeDataSetException {

        if (predicate == null) {
            return false;
        }

        if (predicate.test(getValue())) {
            return setItemCurrent();
        }

        if (isLeaf()) {
            return false;
        }

        for (ITreeDataSetItem<P> child : getChildrenList()) {
            if (child.findItem(predicate)) {
                return true;
            }
        }

        return false;
    }

    /** */
    default ITreeDataSetPath<P, ? extends ITreeDataSetItem<P>> getPath() {
        return new TreeDataSetPath<>(this);
    }

    /** */
    default boolean acceptVisitor(Function<ITreeDataSetItem<P>, Boolean> visitor) {

        if (visitor == null) {
            return false;
        }

        Boolean result = visitor.apply(this);

        if (Boolean.FALSE.equals(result)) {
            return false;
        }

        if (isLeaf()) {
            return true;
        }

        for (ITreeDataSetItem<P> child : getChildrenList()) {
            if (!child.acceptVisitor(visitor)) {
                return false;
            }
        }

        return true;
    }

    /** */
    default void fireBeforeRowsEvent(
            DataSetRowEvent.RowOperationEnum operation,
            List<ITreeDataSetItem<P>> items,
            int rowIndex
    ) {
        getDataSet().fireRowsEvent(
                new TreeDataSetRowsEvent<>(
                        getDataSet(),
                        true,
                        operation,
                        items,
                        rowIndex
                )
        );
    }

    /** */
    default void fireAfterRowsEvent(
            DataSetRowEvent.RowOperationEnum operation,
            List<ITreeDataSetItem<P>> items,
            int rowIndex
    ) {
        getDataSet().fireRowsEvent(
                new TreeDataSetRowsEvent<>(
                        getDataSet(),
                        false,
                        operation,
                        items,
                        rowIndex
                )
        );
    }

    /** */
    default void fireLeafEvent(
            boolean before,
            boolean newLeaf
    ) {
        getDataSet().fireItemEvent(
                new TreeDataSetItemEvent<>(this, before, newLeaf)
        );
    }

    /** */
    default void fillPreOrder(List<ITreeDataSetItem<P>> items) {

        if (items == null) {
            return;
        }

        items.add(this);

        if (isLeaf()) {
            return;
        }

        for (ITreeDataSetItem<P> child : getChildrenList()) {
            child.fillPreOrder(items);
        }
    }
}