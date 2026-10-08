package ru.inversion.db.marker;

import ru.inversion.dataset.mark.MarkModeEnum;

public class StringMarkerDaoBase extends MarkerDaoBase<String>{
   /** */
   @Override
   public MarkModeEnum getMarkMode() {
      return MarkModeEnum.MRK_U;
   }
   /** */
   @Override
   public Class<String> getRowIdClass() {
      return String.class;
   }

   @Override
   protected long doCreateMarkerId() {
      return 0;
   }

   @Override
   protected void doInsert( String rowId ) {
   }

   @Override
   protected void doUpdate(String rowId, long newMarkerId) {

   }

   @Override
   protected boolean doIsMarked(String rowId) {
      return false;
   }

   @Override
   protected void doDelete(String rowId) {
   }

   @Override
   protected void doDeleteAll() {

   }
}
