package ru.inversion.db.marker;

import ru.inversion.dataset.mark.MarkModeEnum;

import java.util.function.Consumer;

/** */
public interface IMarkerDao<T extends Comparable<T>> {

   /** */
   long getMarkerId( );

   /** */
   MarkModeEnum getMarkMode( );

   /** */
   Class<T> getRowIdClass( );

   /** */
   void insert( T rowId );

   /** */
   void update( T rowId, long newMarkerId );

   /** */
   boolean isMarked( T rowId );

   /** */
   long count( );

   /** */
   void delete( T rowId );

   /** */
   void deleteAll( );

   /** */
   void addListener( Consumer<MarkerEvent<T>> listener );
   void removeListener( Consumer<MarkerEvent<T>> listener );
}
