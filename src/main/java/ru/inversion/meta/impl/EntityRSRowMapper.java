package ru.inversion.meta.impl;

import ru.inversion.dataset.IRSRowMapper;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TCStorage;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <h6>ResultSet -> Entity row mapper.</h6>
 * <br>
 * EntityRSRowMapper отвечает за чтение записей и мапинг ResultSet.
 */
final class EntityRSRowMapper<P> implements IRSRowMapper<P> {

   private final ResultSet rs;
   private final IEntityMetaData<P> entityMetadata;
   private final List<CPBinding<P>> bindings = new ArrayList<>();

   /** */
   EntityRSRowMapper( ResultSet rs, IEntityMetaData<P> entityMetadata ) throws SQLException
   {
      this.rs             = Checks.Require.object( rs, "rs" );
      this.entityMetadata = Checks.Require.object( entityMetadata, "entityMetadata" );

      final ResultSetMetaData metaData = rs.getMetaData();
      final int columnCount = metaData.getColumnCount();

      final TaskContext tc = TCStorage.INSTANCE().getByConnection( rs.getStatement().getConnection() );

      final Map<String, IEntityProperty<P, ?>> columnMap = entityMetadata.getColumnsMap();

      for( int i = 1; i <= columnCount; i++ )
      {
         String columnKey = metaData.getColumnLabel(i);

         if( S.isNullOrEmpty(columnKey) )
             columnKey = metaData.getColumnName(i);

         IEntityProperty<P, ?> property = findResultSetProperty( columnKey, columnMap, entityMetadata, tc );

         if( property == null || property.isReadOnly() )
             continue;

         int columnType        = metaData.getColumnType(i);
         String columnTypeName = metaData.getColumnTypeName(i);

         IStructMapper<?> structMapper = null;

         if( columnType == Types.STRUCT
             &&
            ( property.getType() == Object.class || !property.getType().isPrimitive() )
         )
             structMapper = EntityMetadataFactory.getEntityMetaData( property.getType() ).getStructMapper( tc, columnTypeName );

         bindings.add( new CPBinding<>( property, i, columnKey, columnType, columnTypeName, structMapper ) );
      }
   }

   @Override
   public ResultSet getResultSet() {
      return rs;
   }

   @Override
   public P mapRow( int rowNum ) throws SQLException {

      try {

         P pojo = entityMetadata.newInstance();

         for( CPBinding<P> binding : bindings ) {
              mapColumn( pojo, binding );
         }

         return pojo;

      } catch (SQLException ex) {
         throw ex;
      } catch (Throwable th) {
         throw new SQLException( Tags.PRODUCT_LABEL + "Error when mapping row " + rowNum + " from ResultSet to entity " + entityMetadata.getEntityClass().getName(), th );
      }
   }

   /** Readonly свойства не попадают */
   private void mapColumn( P pojo, CPBinding<P> binding ) throws SQLException {

      try {

         Object value = rs.getObject( binding.columnIndex );

         // null из ResultSet не вызывает setter.
         if( value == null )
             return;

         if( binding.structMapper != null )
         {
            binding.property.invokeSetter( pojo, binding.structMapper.from( (Struct)value ) );
            return;
         }

         value = normalizeValue( binding.property, value );

         binding.property.invokeSetter(pojo, value);

      } catch (Throwable th) {
         throw new SQLException(buildColumnMappingErrorMessage(binding), th);
      }
   }

   /** */
   private Object normalizeValue( IEntityProperty<P, ?> property, Object value )
   {
      if( value instanceof BigDecimal && property.isDecimalStripTrailingZeros() && ((BigDecimal) value).scale() > 0)
      {
         BigDecimal bg = ((BigDecimal) value).stripTrailingZeros();

         if( bg.scale() < 0 )
             bg = bg.setScale(0, RoundingMode.HALF_UP);

         return bg;
      }

      return value;
   }

   /** */
   private String buildColumnMappingErrorMessage( CPBinding<P> binding )
   {
      String propertyName = binding.property.getPropertyName();

      String columnName = binding.property.getColumnInfo() == null
                        ? binding.columnKey
                        : binding.property.getColumnInfo().getName();

      Class<?> propertyType = binding.property.getType();

      return Tags.PRODUCT_LABEL
              + "Error mapping ResultSet column '"
              + columnName
              + "'"
              + " / alias '"
              + binding.columnKey
              + "'"
              + " to property '"
              + propertyName
              + "'"
              + " of entity "
              + entityMetadata.getEntityClass().getName()
              + ", columnIndex="
              + binding.columnIndex
              + ", sqlType="
              + binding.sqlType
              + ", sqlTypeName="
              + binding.sqlTypeName
              + ", propertyType="
              + (propertyType == null ? null : propertyType.getName());
   }

   /** */
   private static <P> IEntityProperty<P, ?> findResultSetProperty(
        String columnKey,
        Map<String, IEntityProperty<P, ?>> columnMap,
        IEntityMetaData<P> entityMetadata,
        TaskContext tc
   )
   {
      if( S.isNullOrEmpty(columnKey) )
         return null;

      IEntityProperty<P, ?> property = columnMap.get(columnKey);

      if( property != null )
          return property;

      property = entityMetadata.getProperty(columnKey);

      if( property != null )
          return property;

      String dialectKey = tc.dialect().sqlName(columnKey);

      if( S.isNullOrEmpty(dialectKey) || columnKey.equals(dialectKey) )
          return null;

      property = columnMap.get(dialectKey);

      if( property != null )
          return property;

      return entityMetadata.getProperty(dialectKey);
   }

   /**
    * <h6>Column-Property binding.</h6>
    * <p>
    * Описывает связь между колонкой ResultSet и property entity-класса.<br>
    * Создается один раз на ResultSet metadata и потом используется для каждой строки.
    */
   private static final class CPBinding<P>
   {
      private final IEntityProperty<P, ?> property;

      private final int    columnIndex;
      private final String columnKey;

      private final int    sqlType;
      private final String sqlTypeName;

      private final IStructMapper<?> structMapper;

      private CPBinding (
           IEntityProperty<P, ?> property,
           int columnIndex,
           String columnKey,
           int sqlType,
           String sqlTypeName,
           IStructMapper<?> structMapper
      )
      {
         this.property = property;
         this.columnIndex = columnIndex;
         this.columnKey = columnKey;
         this.sqlType = sqlType;
         this.sqlTypeName = sqlTypeName;
         this.structMapper = structMapper;
      }
   }
}