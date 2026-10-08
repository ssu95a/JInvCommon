package ru.inversion.meta.impl;

import ru.inversion.meta.IEntityProperty;

import java.sql.Types;

final class StructAttributeBinding<P> {

   private final int attributeIndex; // 1-based, как ResultSetMetaData
   private final String attributeName;
   private final IEntityProperty<P, ?> property;
   private final int sqlType;
   private final String sqlTypeName;

   /** */
   StructAttributeBinding(
      int attributeIndex,
      String attributeName,
      IEntityProperty<P, ?> property,
      int sqlType,
      String sqlTypeName
   )
   {
      this.attributeIndex = attributeIndex;
      this.attributeName = attributeName;
      this.property      = property;
      this.sqlType       = sqlType;
      this.sqlTypeName   = sqlTypeName;
   }

   int getAttributeIndex() {
      return attributeIndex;
   }

   int getArrayIndex() {
      return attributeIndex - 1;
   }

   String getAttributeName() {
      return attributeName;
   }

   IEntityProperty<P, ?> getProperty() {
      return property;
   }

   boolean hasProperty() {
      return property != null;
   }

   int getSqlType() {
      return sqlType;
   }

   String getSqlTypeName() {
      return sqlTypeName;
   }

   boolean isStruct() {
      return sqlType == Types.STRUCT;
   }
}