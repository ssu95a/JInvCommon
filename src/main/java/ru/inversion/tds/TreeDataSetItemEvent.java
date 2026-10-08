package ru.inversion.tds;

/** */
public class TreeDataSetItemEvent<P> extends TreeDataSetEventBase<P> {

    /**
     * Типы действий над элементами DataSet
     */
    public enum ItemEventType {

        /**
         * Изменение значения на узле
         */
        CHANGE_VALUE,

        /**
         * Изменение состояния - листовой/узловой
         * */
        CHANGE_LEAF
    }

    final private ITreeDataSetItem<P> treeItem;

    final private P oldValue, newValue;

    final private ItemEventType eventType;

    final private boolean isLeaf;

    /** */
    public TreeDataSetItemEvent(ITreeDataSetItem<P> treeItem, boolean before, P oldValue, P newValue ) {
        super( treeItem.getDataSet(), before );
        this.treeItem = treeItem;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.eventType= ItemEventType.CHANGE_VALUE;
        this.isLeaf   = false;
    }

    /** */
    public TreeDataSetItemEvent(ITreeDataSetItem<P> treeItem, boolean before, boolean isLeaf ) {
        super( treeItem.getDataSet(), before );
        this.treeItem = treeItem;
        this.oldValue = null;
        this.newValue = null;
        this.eventType= ItemEventType.CHANGE_LEAF;
        this.isLeaf   = isLeaf;
    }

    /** */
    public ITreeDataSetItem<P> getTreeItem() {
        return treeItem;
    }

    public P getOldValue() {
        return oldValue;
    }

    public P getNewValue() {
        return newValue;
    }

    public ItemEventType getEventType() {
        return eventType;
    }

    public boolean isLeafOld() {
        return !isLeaf;
    }

    public boolean isLeafNew() {
        return isLeaf;
    }
}
