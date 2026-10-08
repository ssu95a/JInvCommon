package ru.inversion.tds;

/** */
public interface ITreeDataSetItemFactory {
    /** */
    <P> ITreeDataSetItem<P> createItem( ITreeDataSetItem<P> parent, P value );
    /** */
    <P> ITreeDataSetItem<P> createRootItem( ITreeDataSet<P> treeDataSet, P value );
}
