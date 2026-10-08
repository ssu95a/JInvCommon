package ru.inversion.db.marker;

import ru.inversion.dataset.mark.MarkModeEnum;

public abstract class Id2MarkerDaoBase extends MarkerDaoBase<ID2>{

   @Override
   public MarkModeEnum getMarkMode() {
      return MarkModeEnum.MRK_ID2;
   }

   @Override
   public Class<ID2> getRowIdClass() {
      return ID2.class;
   }
}
