package ru.inversion.meta.impl;

import ru.inversion.dataset.IStructMapper;
import ru.inversion.meta.EntityMetaDataException;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.tc.TCStorage;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.converter.TypeConverterException;

import java.io.InputStream;
import java.io.Reader;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class EntityStructMapper<T> implements IStructMapper<T> {

   private final String structTypeName;
   private final IEntityMetaData<T> entityMetaData;
   private final List<StructAttributeBinding<T>> bindings;

   /**
    * list должен сохранять позиционность STRUCT attributes.
    *
    * Даже если property == null, binding остаётся на своей позиции.
    */
   EntityStructMapper (
      String structTypeName,
      IEntityMetaData<T> entityMetaData,
      List<StructAttributeBinding<T>> bindings
   )
   {
      this.structTypeName = Objects.requireNonNull(structTypeName, "'structTypeName' is null");
      this.entityMetaData = Objects.requireNonNull(entityMetaData, "'entityMetaData' is null");
      this.bindings       = Collections.unmodifiableList( new ArrayList<>(Objects.requireNonNull(bindings, "'bindings' is null")) );
   }

   /** */
   @Override
   public Struct toStruct(TaskContext tc, T value) throws SQLException {

      if( value == null )
          return null;

      try {

         TaskContext resolvedTc = resolveTaskContext(tc);

         final Object[] attributes = new Object[bindings.size()];

         for( StructAttributeBinding<T> binding : bindings )
         {
              attributes[binding.getArrayIndex()] = toStructAttribute( resolvedTc, binding, value );
         }

         return resolvedTc.dialect().createStruct(
                 resolvedTc,
                 structTypeName,
                 attributes
         );

      } catch (SQLException ex) {
         throw ex;
      } catch (Throwable th) {
         throw new SQLException(
                 Tags.PRODUCT_LABEL
                         + "Error convert Java class "
                         + entityMetaData.getEntityClass().getName()
                         + " to JDBC Struct '"
                         + structTypeName
                         + "'",
                 th
         );
      }
   }

   /** */
   private Object toStructAttribute (
      TaskContext tc,
      StructAttributeBinding<T> binding,
      T value
   )
      throws SQLException
   {
      if( binding == null || binding.getProperty() == null )
          return null;

      Object v = binding.getProperty().invokeGetter(value);

      if( v == null )
          return null;

      if( v instanceof Reader )
          return TypeConverter.convert(v, char[].class);

      if( v instanceof InputStream )
          return TypeConverter.convert(v, byte[].class);

      if( v instanceof Struct )
          return v;

      /*
       * Nested STRUCT.
       *
       */
      if (
         binding.getSqlType() == Types.STRUCT
         &&
         S.isNotNullOrEmpty( binding.getSqlTypeName() )
      )
      {
         IStructMapper<Object> nestedMapper = StructMetaData.get(tc, binding.getSqlTypeName()).getMapper((Class<Object>) v.getClass());

         return nestedMapper.toStruct(tc, v);
      }

      return Jt2St.convert(v);
   }

   /** */
   @Override
   public T from(Struct value) {

      if( value == null )
          return null;

      try {

         T pojo = entityMetaData.newInstance();

         final Object[] attributes = value.getAttributes();

         if( attributes == null )
             throw new IllegalStateException( Tags.PRODUCT_LABEL + "Struct attributes is null for '" + structTypeName + "'" );

         for( int i = 0; i < bindings.size(); i++)
         {
              StructAttributeBinding<T> binding = bindings.get(i);

              if( binding == null || !binding.hasProperty() )
                  continue;

             //  Read-only свойства не устанавливаются для STRUCT -> POJO mapping.
            if( binding.getProperty().isReadOnly() )
                continue;

            Object attribute = i < attributes.length ? attributes[binding.getArrayIndex()] : null;

            binding.getProperty().invokeSetter(pojo, attribute);
         }

         return pojo;

      } catch (Throwable th) {
         TypeConverterException.fromValueToClass( value, entityMetaData.getEntityClass(), th, "Struct_to_Object");
      }

      return null;
   }

   private static TaskContext resolveTaskContext(TaskContext tc) throws SQLException {

      TaskContext resolvedTc = U.nvl( TCStorage.INSTANCE().getBy(tc1 -> Boolean.TRUE.equals(tc1.getProperty("common"))), tc );

      if( resolvedTc == null)
          throw new SQLException( Tags.PRODUCT_LABEL + "TaskContext is required for STRUCT mapping." );

      return resolvedTc;
   }

   @Override
   public String getSQLTypeName() {
      return structTypeName;
   }

}