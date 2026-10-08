package ru.inversion.meta.impl;

import ru.inversion.dataset.IRSRowMapper;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.meta.EntityMetaDataException;
import ru.inversion.meta.IEntityDao;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Новый класс хранения метаданных.
 */
public final class EntityMetadata<P> implements IEntityMetaData<P>, IDumpable {

   private final Class<P> entityClass;
   private final String name;
   private final String table;

   private final Map<String, IEntityProperty<P, ?>> propertiesMap;
   private final Map<String, IEntityProperty<P, ?>> columnMap;
   private final List<IEntityProperty<P, ?>> idList;

   private volatile IRowMapper<P> rowMapper;

   private transient volatile IJsonMapper<P> jsonMapper;


   static <P> Comparator<IEntityProperty<P, ?>> columnComparator() {

      return (c1, c2) -> {

         if( c1 == c2 )
            return 0;
         if( c1.isId() && c2.isId() )
            return c1.getColumnName().compareToIgnoreCase(c2.getColumnName());
         if( c1.isId() )
            return -1;
         if( c2.isId() )
            return 1;
         return c1.getColumnName().compareToIgnoreCase(c2.getColumnName());
      };
   }


   /**
    * Package-private специально.
    */
   EntityMetadata(
        Class<P> entityClass,
        String[] names,
        Map<String, IEntityProperty<P, ?>> propertiesMap
   )
   {
      this.entityClass = Checks.Require.object( entityClass, "entityClass");

      if( names == null || names.length < 2 )
          throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Parameter 'names' must contain table and entity name");

      this.table = names[0];
      this.name  = names[1];

      final Map<String, IEntityProperty<P, ?>> propertiesCopy = new LinkedHashMap<>( Objects.requireNonNull(propertiesMap, "propertiesMap is null") );

      this.propertiesMap = Collections.unmodifiableMap(propertiesCopy);

      this.idList = Collections.unmodifiableList(
         propertiesCopy.values()
            .stream()
               .filter(IEntityProperty::isId)
                  .collect(Collectors.toList())
      );

      final List<IEntityProperty<P, ?>> columnList =
         propertiesMap.values()
             .stream()
             .filter(p -> p.getColumnInfo() != null)
             .sorted(columnComparator()) // ID-колонки оставляем первыми
         .collect(Collectors.toList());

      this.columnMap = new ListMap<>( columnList, IEntityProperty::getColumnName, String.CASE_INSENSITIVE_ORDER, true );
   }

   @Override
   public void dump( Map<String, Object> properties ) {
   }

   @Override
   public Class<P> getEntityClass() {
      return entityClass;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public String getTableName() {
      return table;
   }

   @Override
   public IEntityProperty<P, ?> getProperty(String property) {

      if( S.isNullOrEmpty(property) )
          return null;

      IEntityProperty<P, ?> p = propertiesMap.get(property);
      if( p != null )
          return p;

      return columnMap.get(property);
   }

   @Override
   public List<IEntityProperty<P,?>> getIDList() {
      return idList;
   }

   @Override
   public Map<String, IEntityProperty<P, ?>> getPropertiesMap() {
      return propertiesMap;
   }

   @Override
   public Map<String, IEntityProperty<P, ?>> getColumnsMap() {
      return columnMap;
   }

   @Override
   public IJsonMapper<P> getJSonMapper() {

      IJsonMapper<P> jm = jsonMapper;

      if( jm == null )
      {
         synchronized (this) {

            jm = jsonMapper;

            if (jm == null) {
               jm = new EntityJsonSupport<>(this);
               jsonMapper = jm;
            }
         }
      }

      return jm;
   }

   @Override
   public IRowMapper<P> getRowMapper() {

      IRowMapper<P> rm = rowMapper;

      if( rm == null )
      {
         synchronized (this) {
            rm = rowMapper;
            if( rm == null ) {
               rm = createRowMapper();
               rowMapper = rm;
            }
         }
      }

      return rm;
   }

   @Override
   public IEntityDao<P, Serializable> getEntityDao(TaskContext tc) {
      return new EntityDao<>(this, tc);
   }

   @Override
   public IRSRowMapper<P> createRSRowMapper(ResultSet rs) {
      try {
         Objects.requireNonNull(rs, "ResultSet parameter 'rs' is null");
         return new EntityRSRowMapper<>(rs, this);
      } catch (SQLException ex) {
         throw new EntityMetaDataException(Tags.PRODUCT_LABEL + "Error on createRSRowMapper", ex);
      }
   }

   /** */
   private IRowMapper<P> createRowMapper()
   {
      return new EntityRowMapper<>(this);
   }

   @Override
   public IStructMapper<P> getStructMapper( TaskContext tc, String structTypeName )
   {
      if( S.isNullOrEmpty(structTypeName) )
         return null;

      return StructMetaData.get(tc, structTypeName).getMapper(getEntityClass());
   }


   @Override
   public Comparator<P> getIdComparator() {

      List<IEntityProperty<P, ?>> l = getIDList();

      if( l == null || l.isEmpty() )
         throw new IllegalStateException(Tags.PRODUCT_LABEL + "Id list is empty. No comparator by Id.");


      if(l.size() == 1)
      {
         IEntityProperty<P, ?> prpId = l.get(0);

         return new Comparator<P>() {
            @Override
            public int compare(P o1, P o2) {
               Object id1 = prpId.invokeGetter(o1);
               Object id2 = prpId.invokeGetter(o2);

               /*
                * Это не настоящий сортировочный Comparator, а equality-comparator по id.
                */
               return U.equals( id1, id2 ) ? 0 : -1;
            }
         };
      }

      return new Comparator<P>() {
         @Override
         public int compare(P o1, P o2) {
            for (IEntityProperty<P, ?> prpId : l) {
               Object id1 = prpId.invokeGetter(o1);
               Object id2 = prpId.invokeGetter(o2);

               if (!U.equals(id1, id2)) {
                  return -1;
               }
            }

            return 0;
         }
      };
   }

   /** */
   public P newInstance() {

      try {
         return getEntityClass().getDeclaredConstructor().newInstance();
      }
      catch (Throwable th) {
         throw new EntityMetaDataException(
                 Tags.PRODUCT_LABEL
                         + "Can not create entity instance of class "
                         + getEntityClass().getName()
                         + ". Entity class must have accessible no-args constructor.",
                 th
         );
      }
   }


}