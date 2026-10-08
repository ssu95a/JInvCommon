package ru.inversion.tds;

import ru.inversion.dataset.AbstractDataSetBase;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.util.*;

/** */
public class TreeDataSetItem<P> implements ITreeDataSetItem<P> {

   private List<ITreeDataSetItem<P>> children;
   private P value;
   private ITreeDataSet<P> dataSet;
   private ITreeDataSetItem<P> parent;

   /** */
   public TreeDataSetItem(ITreeDataSet<P> dataSet, P value) {
      this.dataSet = dataSet;
      this.parent = null;
      this.value = value;
   }

   /** */
   public TreeDataSetItem(ITreeDataSet<P> dataSet) {
      this.dataSet = dataSet;
      this.parent = null;
      this.value = null;
   }

   /** */
   public TreeDataSetItem(
           ITreeDataSetItem<P> parent,
           P value,
           boolean construct
   ) {
      this.dataSet = null;
      this.parent = Objects.requireNonNull(parent, "'parent' is null");
      this.value = value;

      if (construct) {
         this.parent.addChild(this);
      }
   }

   /** */
   public TreeDataSetItem(TreeDataSetItem<P> parent) {
      this.dataSet = null;
      this.parent = Objects.requireNonNull( parent, "'parent' is null");
   }

   /** */
   @Override
   public ITreeDataSetItem<P> newChildItem(P value) {
      return new TreeDataSetItem<>(this, value, false);
   }

   /** */
   @Override
   public void addChild(ITreeDataSetItem<P> child) {

      if (child == null) {
         return;
      }

      attachChild(child);

      checkChildrenCollection();

      if (!children().contains(child)) {
         children().add(child);
      }
   }


   /** */
   private void validateChildForAttach(ITreeDataSetItem<P> child)
   {
      Objects.requireNonNull(child, "'child' is null");

      if( child == this )
         throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "TreeDataSet: Can not add item as child of itself" );

      ITreeDataSetItem<P> parentItem = getParentItem();

      while( parentItem != null )
      {
         if( parentItem == child )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "TreeDataSet: Can not add ancestor item as child" );

         parentItem = parentItem.getParentItem();
      }

      if( child instanceof TreeDataSetItem )
      {
         TreeDataSetItem<P> item = (TreeDataSetItem<P>) child;

         if( item.parent != null && item.parent != this )
             throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "TreeDataSet: Child item already has another parent" );
      }
      else
         if( child.getParentItem() != this )
         {
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "TreeDataSet: Child item has incompatible parent" );
         }
   }

   /** */
   @Override
   public void addChildrenAt(
           int position,
           List<ITreeDataSetItem<P>> newItems
   )
   {
      if( newItems == null || newItems.isEmpty() )
         return;

      checkChildrenCollection();

      int safePosition = position;

      if( safePosition < 0 )
          safePosition = 0;

      if( safePosition > children().size() )
          safePosition = children().size();

      final List<ITreeDataSetItem<P>> items = new ArrayList<>(newItems.size());

      for( ITreeDataSetItem<P> item : newItems )
      {
         validateChildForAttach(item);

         if( children().contains(item) || items.contains(item) )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "TreeDataSet: Child item is already attached to this parent" );

         items.add(item);

      }

      for( ITreeDataSetItem<P> item : items )
           attachChild(item);

      children().addAll( safePosition, items );
   }

   /** */
   @Override
   public void removeChildren(Collection<ITreeDataSetItem<P>> items) {

      if (children == null || items == null || items.isEmpty()) {
         return;
      }

      children.removeAll(items);

      for( ITreeDataSetItem<P> item : items )
           detachChild(item);


      if( children.isEmpty() )
          children = null;

   }

   /** */
   protected void checkChildrenCollection() {
      if( this.children == null )
          this.children = AbstractDataSetBase.getDataSetListFactory().createDataSetList();
   }

   /** */
   private List<ITreeDataSetItem<P>> children() {
      return children;
   }

   /** */
   @Override
   public List<ITreeDataSetItem<P>> getChildrenList()
   {
      return children == null ? null : Collections.unmodifiableList(children);
   }


   /** */
   @Override
   public ITreeDataSetItem<P> getParentItem() {
      return parent;
   }


   /** */
   @Override
   public ITreeDataSet<P> getDataSet() {

      if( dataSet != null )
          return dataSet;

      if( parent == null )
          return null;

      return parent.getDataSet();
   }


   /** */
   @Override
   public void setValue(P v)
   {
      if( value == v)
          return;

      final P oldValue = value;

      value = v;

      final ITreeDataSet<P> ds = getDataSet();

      if( ds != null )
          ds.fireItemEvent( new TreeDataSetItemEvent<>( this, false, oldValue, v ) );
   }


   /** */
   @Override
   public P getValue() {
      return value;
   }

   /** */
   @Override
   public void executeQuery() { }


   /** */
   private void attachChild(ITreeDataSetItem<P> child)
   {
      validateChildForAttach(child);

      if( child instanceof TreeDataSetItem )
      {
         TreeDataSetItem<P> item = (TreeDataSetItem<P>) child;

         item.parent = this;
         item.dataSet= null;
      }
   }

   /** */
   private void detachChild(ITreeDataSetItem<P> child) {

      if( !(child instanceof TreeDataSetItem) )
          return;

      TreeDataSetItem<P> item = (TreeDataSetItem<P>) child;

      if(item.parent == this) {
         item.parent = null;
         item.dataSet = null;
      }
   }
}