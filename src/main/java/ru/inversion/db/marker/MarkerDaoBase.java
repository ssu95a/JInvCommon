package ru.inversion.db.marker;

import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.util.function.Consumer;

import static ru.inversion.db.marker.MarkerEvent.MarkOperationEnum.*;

/** */
public abstract class MarkerDaoBase<T extends Comparable<T>> implements IMarkerDao<T> {

   private long markerId;

   /** */
   public MarkerDaoBase( ) {
      this.markerId = -1;
   }

   /** */
   public MarkerDaoBase( long markerId ) {
      this.markerId = markerId;
   }

   /** */
   protected abstract long doCreateMarkerId();

   /** */
   @Override
   public long getMarkerId( )
   {
      if( markerId == -1L )
          markerId = doCreateMarkerId();
      return markerId;
   }

   /** */
   protected abstract void doInsert( T rowId );

   @Override
   public void insert( T rowId ) {
      if( rowId != null ) {
          doInsert(rowId);
          fire( rowId, INSERT );
      }
   }

   /** */
   protected abstract void doUpdate( T rowId, long newMarkerId );

   @Override
   public void update( T rowId, long newMarkerId ) {
      if( rowId != null ) {
          doUpdate( rowId, newMarkerId );
          fire( rowId, UPDATE );
      }
   }

   /** */
   protected abstract boolean doIsMarked( T rowId );

   @Override
   public boolean isMarked( T rowId ) {
      if( rowId == null )
          return false;
      return doIsMarked(rowId);
   }

   @Override
   public long count() {
      return 0;
   }

   /** */
   protected abstract void doDelete( T rowId );

   /** */
   @Override
   public void delete( T rowId ) {
      if( rowId != null ) {
         doDelete( rowId );
         fire( rowId, DELETE );
      }
   }

   /** */
   protected abstract void doDeleteAll( );

   @Override
   public void deleteAll() {
      doDeleteAll( );
      fire( null, DELETE_ALL );
   }

   private IListenerManEvent<Consumer<MarkerEvent<T>>,MarkerEvent<T>> listeners;

   @Override
   public void addListener( Consumer<MarkerEvent<T>> listener ) {

      if( listener == null )
          return;

      if( listeners == null )
          listeners = ListenerManFactory.createListenerManEvent( Consumer<MarkerEvent<T>>::accept );

      listeners.addListener(listener);
   }

   @Override
   public void removeListener(Consumer<MarkerEvent<T>> listener) {

      if( listener == null || listeners == null )
          return;
      listeners.removeListener(listener);
      if( listeners.isEmpty() )
          listeners = null;
   }

   /** */
   protected void fire( T rowId, MarkerEvent.MarkOperationEnum operation ) {
      if( listeners == null )
          return;
      listeners.fire( new MarkerEvent<>(this, operation, rowId) );
   }

}
