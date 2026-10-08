package ru.inversion.db.marker;

import ru.inversion.dataset.mark.MarkModeEnum;

public abstract class LongMarkerDaoBase extends MarkerDaoBase<Long> {
   /** */
   @Override
   public MarkModeEnum getMarkMode() {
      return MarkModeEnum.MRK_ID;
   }
   @Override
   public Class<Long> getRowIdClass() {
      return Long.class;
   }
}
