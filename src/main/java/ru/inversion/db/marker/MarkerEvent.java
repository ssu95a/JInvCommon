package ru.inversion.db.marker;

import ru.inversion.dataset.mark.MarkModeEnum;

import java.util.EventObject;


public class MarkerEvent<T extends Comparable<T>> extends EventObject {

   /** Тип действия по маркеру. */
   public enum MarkOperationEnum {

      /** Вставить одну запись. */
      INSERT,

      /** Обновить пометку с одной записи. */
      UPDATE,

      /**  Удалить одну запись */
      DELETE,

      /**  Удалить все записи. */
      DELETE_ALL
   };

   final private MarkOperationEnum operation;
   final private T rowId;

   /** {@inheritDoc} */
   public MarkerEvent( Object source, MarkOperationEnum operation, T rowId ) {
      super(source);
      this.operation= operation;
      this.rowId    = rowId;
   }

   /** */
   public long markerId() {
      return ((IMarkerDao)getSource()).getMarkerId();
   }

   /** */
   public MarkModeEnum markMode() {
      return ((IMarkerDao)getSource()).getMarkMode();
   }

   /** */
   public MarkOperationEnum operation() {
      return operation;
   }

   /** */
   public T rowId() {
      return rowId;
   }
}
