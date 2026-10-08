package ru.inversion.meta.impl;

import ru.inversion.dataset.mark.ID2Markable;
import ru.inversion.dataset.mark.IDMarkable;
import ru.inversion.dataset.mark.IID2Markable;
import ru.inversion.dataset.mark.IIDMarkable;
import ru.inversion.dataset.mark.IMarkable;
import ru.inversion.dataset.mark.IRowIDMarkable;
import ru.inversion.dataset.mark.IUMarkable;
import ru.inversion.dataset.mark.MarkableBase;
import ru.inversion.dataset.mark.RowIDMarkable;
import ru.inversion.dataset.mark.UMarkable;
import ru.inversion.db.entity.ColumnSQLDefinition;
import ru.inversion.db.entity.Content;
import ru.inversion.db.entity.DBReturningValue;
import ru.inversion.db.entity.EntityAttribute;
import ru.inversion.db.entity.ProxyColumns;
import ru.inversion.db.entity.ProxyFor;
import ru.inversion.db.entity.Required;
import ru.inversion.meta.EntityMetaDataException;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Новая загрузка Entity metadata.
 */
public final class EntityMetadataIntrospector {

   private EntityMetadataIntrospector() { }

   /** */
   public static <P> EntityMetadata<P> introspect(Class<P> entityClass)
   {

      Objects.requireNonNull( entityClass, "entityClass is null");

      try {

         Map<String, IEntityProperty<P, ?>> propertiesMap = new LinkedHashMap<>();
         List<Pair<IEntityProperty<P, ?>, String[]>> proxyColumnList = new LinkedList<>();

         List<BeanProperty> beanProperties = collectBeanProperties(entityClass);

         for( BeanProperty bp : beanProperties )
         {

            final AccessorPair accessors = selectAccessors( entityClass, bp );

            if( accessors.isEmpty() )
                continue;

            AnnotationPair<Column> columnHit = annotationHit(accessors, bp.name, Column.class );

            if( columnHit == null )
                continue;

            Column column = columnHit.annotation();

            if( S.isNullOrEmpty( column.name()) || IMarkable.MARK_COLUMN.equals(column.name()) )
                continue;

            PropertyDescriptor pd = createPropertyDescriptor(bp.name, accessors);

            String propertyName = bp.name;

            if (propertiesMap.containsKey(propertyName)) {
               throw new EntityMetaDataException(
                       Tags.PRODUCT_LABEL
                               + "Duplicate property metadata '"
                               + propertyName
                               + "' in class "
                               + entityClass.getName()
               );
            }

            Map<String, Object> values = new HashMap<>();

            collectPropertyAnnotations(accessors, bp.name, pd.getPropertyType(), values);

            values.put("column.name",       column.name());
            values.put("column.insertable", column.insertable());
            values.put("column.updatable",  column.updatable());
            values.put("column.nullable",   column.nullable());
            values.put("column.table",      column.table());
            values.put("column.unique",     column.unique());

            Class<?> propertyType = pd.getPropertyType();

            if (U.in(propertyType, String.class, Long.class, Integer.class) && column.length() != 255) {
               values.put("column.length", column.length());
            }

            if (!S.isNullOrEmpty(column.columnDefinition())) {
               values.put("column.query_expr", column.columnDefinition());
            }

            AnnotationPair<ColumnSQLDefinition> sqlDefHit = annotationHit( accessors, bp.name, ColumnSQLDefinition.class);

            if( sqlDefHit != null )
            {
               ColumnSQLDefinition cDef = sqlDefHit.annotation();

               final String def = (String) values.get("column.query_expr");

               final Function<LangEnum, String> queryExprSupplier = new Function<LangEnum, String>() {
                  private final String queryExprDef = def;
                  private final LangEnum lang = cDef.lang();
                  private final String queryExpr = cDef.columnDefinition();

                  @Override
                  public String apply(LangEnum l) {
                     return l == lang ? queryExpr : queryExprDef;
                  }
               };

               values.put("column.query_expr", queryExprSupplier);
            }

            /*
             * @Column + @Transient => propertyName становится равен column.name().
             */
            if( values.containsKey("transient") ) {
                 propertyName = column.name();
            }

            EntityProperty<P, ?> ep = new EntityProperty<>(propertyName, column.name(), pd, values);

            propertiesMap.put(propertyName, ep);

            AnnotationPair<ProxyColumns> proxyColumnsHit = annotationHit( accessors, bp.name, ProxyColumns.class );

            if (proxyColumnsHit != null
                    && proxyColumnsHit.annotation().columnNames().length > 0) {

               proxyColumnList.add(
                  Pair.makePair(ep, proxyColumnsHit.annotation().columnNames())
               );
            }
         }

         /*
          * Второй проход: transient/calculated свойства.
          */
         for (BeanProperty bp : beanProperties) {

            AccessorPair accessors = selectAccessors(entityClass, bp);

            /*
             * Write-only свойства без @Column здесь не нужны.
             */
            if( accessors.readMethod() == null) {
                continue;
            }

            /*
             * Если property имеет @Column на выбранном getter/setter,
             * оно уже должно было быть обработано первым проходом.
             */
            if( annotationHit(accessors, bp.name, Column.class) != null) {
               continue;
            }

            if( propertiesMap.containsKey(bp.name) ) {
               continue;
            }

            PropertyDescriptor pd = createPropertyDescriptor(bp.name, accessors);

            Map<String, Object> values = new HashMap<>();

            values.put("transient", Boolean.TRUE);

            collectPropertyAnnotations(accessors, bp.name, pd.getPropertyType(), values);

            EntityProperty<P, ?> transientProperty =
                    new EntityProperty<>(bp.name, bp.name, pd, values);

            propertiesMap.put(bp.name, transientProperty);

            AnnotationPair<ProxyFor> proxyForHit =
                    annotationHit(accessors, bp.name, ProxyFor.class);

            if( proxyForHit != null)
            {
               IEntityProperty<P, ?> proxyForProperty = findProperty(propertiesMap, proxyForHit.annotation().columnName());
               if( proxyForProperty != null )
                  transientProperty.setProxyFor(proxyForProperty, proxyForHit.annotation());
            }
         }

         /*
          * Обработка @ProxyColumns.
          */
         for (Pair<IEntityProperty<P, ?>, String[]> p : proxyColumnList) {

            for (String columnName : p.second) {

               EntityProperty<P, ?> property =
                       (EntityProperty<P, ?>) findProperty(propertiesMap, columnName);

               if (property != null && property.getProxyFor() == null) {
                  property.setProxyFor(p.first);
               }
            }
         }

         return new EntityMetadata<>(entityClass, resolveNames(entityClass), propertiesMap);

      } catch (EntityMetaDataException ex) {
         throw ex;
      } catch (Exception ex) {
         throw new EntityMetaDataException(
                 Tags.PRODUCT_LABEL
                         + "Error introspecting entity metadata for class "
                         + entityClass.getName(),
                 ex
         );
      }
   }

   /** */
   private static PropertyDescriptor createPropertyDescriptor(
        String propertyName,
        AccessorPair accessors
   )
   {
      try {
         return new PropertyDescriptor(
              propertyName,
              accessors.readMethod(),
              accessors.writeMethod()
         );
      } catch (IntrospectionException ex) {
         throw new EntityMetaDataException(
              Tags.PRODUCT_LABEL
                   + "Invalid JavaBeans accessor pair for property '"
                   + propertyName
                   + "': readMethod="
                   + U.callIfNotNull( accessors.readMethod(), Method::toGenericString )
                   + ", writeMethod="
                   + U.callIfNotNull( accessors.writeMethod(), Method::toGenericString),
              ex
      );
      }
   }

   /** */
   private static List<BeanProperty> collectBeanProperties( Class<?> entityClass )
   {
      final Map<String, BeanProperty> properties = new LinkedHashMap<>();

      for( Method method : entityClass.getMethods() )
      {
         if(!isOkMethod(method) )
            continue;

         String propertyName = getBeanPropertyName(method);

         if( propertyName == null )
             continue;

         BeanProperty bp = properties.computeIfAbsent( propertyName, BeanProperty::new );

         if( isGetter(method) )
             bp.getters.add(method);
         else
            if( isSetter(method) )
                bp.setters.add(method);
      }

      return properties.values().stream().sorted(Comparator.comparing(bp -> bp.name)).collect(Collectors.toList());
   }


   /** */
   private static String decapitalizePropertyName(String name) {

      if( S.isNullOrEmpty(name) )
          return name;

      if( name.length() > 1 && name.equals(name.toUpperCase(java.util.Locale.ROOT)) )
         return name;

      return Introspector.decapitalize(name);
   }


   /** */
   private static String getBeanPropertyName( Method method )
   {
      String methodName = method.getName();

      if( isGetter(method) )
      {
         if( methodName.startsWith("get") )
            return decapitalizePropertyName(methodName.substring(3));

         if( methodName.startsWith("is") )
            return decapitalizePropertyName(methodName.substring(2));
      }

      if( isSetter(method) )
         return decapitalizePropertyName(methodName.substring(3));

      return null;
   }


   /** */
   private static boolean isGetter( Method method )
   {
      if( method == null || method.getParameterCount() != 0 )
         return false;

      if( method.getReturnType() == Void.TYPE )
         return false;

      String name = method.getName();

      if( "getClass".equals(name) || "isMark".equals(name) )
         return false;

      if( name.startsWith("get") && name.length() > 3 )
         return true;

      if( name.startsWith("is") && name.length() > 2 )
         return method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class;

      return false;
   }

   /** */
   private static boolean isSetter(Method method) {

      if( method == null )
         return false;

      return method.getName().startsWith("set") && method.getName().length() > 3 && method.getParameterCount() == 1 && method.getReturnType() == Void.TYPE;
   }

   /** */
   private static boolean isOkMethod( Method method ) {

      if( method == null )
         return false;

      return !U.in(
              method.getDeclaringClass(),
              Object.class,
              UMarkable.class,
              MarkableBase.class,
              IMarkable.class,
              IDMarkable.class,
              ID2Markable.class,
              RowIDMarkable.class,
              IIDMarkable.class,
              IID2Markable.class,
              IRowIDMarkable.class,
              IUMarkable.class
      );
   }

   /** */
   private static AccessorPair selectAccessors( Class<?> entityClass, BeanProperty bp )
   {
      List<Method> getters = bp.getters
                  .stream()
                  .sorted(getterComparator(entityClass))
                  .collect(Collectors.toList());

      List<Method> setters = bp.setters
                  .stream()
                  .sorted(setterComparatorWithoutGetter(entityClass))
                  .collect(Collectors.toList());

      if( !getters.isEmpty() )
      {
         for( Method getter : getters )
         {
            Optional<Method> setter = findBestCompatibleSetter(entityClass, getter, setters);

            if( setter.isPresent() || setters.isEmpty() )
                return new AccessorPair(getter, setter.orElse(null));
         }

         // Getter есть, но ни один setter с ним не совместим.
         // Значит: property read-only.
         return new AccessorPair(getters.get(0), null);
      }

      if (!setters.isEmpty()) {
         return new AccessorPair(null, setters.get(0));
      }

      return AccessorPair.EMPTY;
   }


   /** */
   private static Optional<Method> findBestCompatibleSetter (
      Class<?> entityClass,
      Method getter,
      List<Method> setters
   )
   {
      Class<?> getterType = getter.getReturnType();
      return setters.stream()
               .filter(setter -> isSetterCompatible(getter, setter))
                  .min( setterComparator( entityClass, getterType ));
   }


   /** */
   private static boolean isSetterCompatible( Method getter, Method setter ) {

      if( getter == null || setter == null )
          return false;

      Class<?> getterType = getter.getReturnType();
      Class<?> setterType = setter.getParameterTypes()[0];

      /*
       * Setter должен принимать то, что возвращает getter.
       *
       * OK:
       * String getValue()
       * void setValue(String value)
       *
       * OK:
       * String getValue()
       * void setValue(CharSequence value)
       *
       * NOT OK:
       * CharSequence getValue()
       * void setValue(String value)
       */
      return setterType.isAssignableFrom(getterType);
   }


   /** */
   private static Comparator<Method> getterComparator(Class<?> entityClass)
   {
      return Comparator
              .comparingInt((Method m) -> declaringDistance(entityClass, m.getDeclaringClass()))
              .thenComparingInt(m -> m.getDeclaringClass().isInterface() ? 1 : 0)
              // обычный метод лучше synthetic/bridge
              .thenComparingInt(m -> m.isBridge() ? 1 : 0)
              .thenComparingInt(m -> m.isSynthetic() ? 1 : 0)
              // более конкретный return type лучше
              .thenComparingInt(m -> -typeDepth(m.getReturnType()))
              .thenComparing(Method::toGenericString);
   }

   /** */
   private static Comparator<Method> setterComparator (
      Class<?> entityClass,
      Class<?> getterType
   )
   {
      return Comparator
              .comparingInt((Method m) -> typeDistance(getterType, m.getParameterTypes()[0]))
              .thenComparingInt(m -> declaringDistance(entityClass, m.getDeclaringClass()))
              .thenComparingInt(m -> m.getDeclaringClass().isInterface() ? 1 : 0)
              // обычный setter лучше bridge/synthetic,
              .thenComparingInt(m -> m.isBridge() ? 1 : 0)
              .thenComparingInt(m -> m.isSynthetic() ? 1 : 0)

              .thenComparing(Method::toGenericString);
   }

   /** */
   private static Comparator<Method> setterComparatorWithoutGetter( Class<?> entityClass )
   {
      return Comparator
              .comparingInt((Method m) -> declaringDistance(entityClass, m.getDeclaringClass()))
              .thenComparingInt(m -> m.getDeclaringClass().isInterface() ? 1 : 0)
              // обычный setter лучше bridge/synthetic
              .thenComparingInt(m -> m.isBridge() ? 1 : 0)
              .thenComparingInt(m -> m.isSynthetic() ? 1 : 0)

              .thenComparingInt(m -> -typeDepth(m.getParameterTypes()[0]))
              .thenComparing(Method::toGenericString);
   }

   /** */
   private static int declaringDistance(Class<?> entityClass, Class<?> declaringClass) {

      if( declaringClass.equals(entityClass) )
         return 0;

      int distance = 1;

      Class<?> c = entityClass.getSuperclass();

      while (c != null) {
         if (c.equals(declaringClass)) {
            return distance;
         }

         c = c.getSuperclass();
         distance++;
      }

      if (declaringClass.isInterface()) {
         return 100 + interfaceDistance(entityClass, declaringClass, 0);
      }

      return 1000;
   }

   private static int interfaceDistance(Class<?> type, Class<?> iface, int depth) {

      if (type == null) {
         return 1000;
      }

      for (Class<?> i : type.getInterfaces()) {
         if (i.equals(iface)) {
            return depth;
         }

         int d = interfaceDistance(i, iface, depth + 1);

         if (d < 1000) {
            return d;
         }
      }

      return interfaceDistance(type.getSuperclass(), iface, depth + 1);
   }

   /** Дистанция по наследованию от детского к родительскому */
   private static int typeDistance( Class<?> child, Class<?> parent )
   {
      if( child == null || parent == null )
         return Integer.MAX_VALUE;

      if( child.equals(parent) )
          return 0;

      if( !parent.isAssignableFrom(child) )
         return Integer.MAX_VALUE;

      int distance = 0;
      Class<?> c = child;

      while( c != null )
      {
         if( c.equals(parent) )
             return distance;

         for( Class<?> i : c.getInterfaces() ) {
            if( i.equals(parent) ) {
               return distance + 1;
            }
         }

         c = c.getSuperclass();

         distance++;
      }

      return 1000;
   }

   /** Глубина наследования для класса, сколько у него родителей */
   private static int typeDepth( Class<?> type )
   {

      if( type == null || type.isPrimitive() )
          return 0;

      int depth = 0;
      Class<?> c = type;

      while( c != null )
      {
         depth++;
         c = c.getSuperclass();
      }

      return depth;
   }

   /** */
   private static void collectPropertyAnnotations(
        AccessorPair accessors,
        String propertyName,
        Class<?> propertyType,
        Map<String, Object> values
   ) {
      if (annotationHit(accessors, propertyName, Transient.class) != null) {
         values.put("transient", Boolean.TRUE);
      }

      if (annotationHit(accessors, propertyName, Required.class) != null) {
         values.put("required", Boolean.TRUE);
      }

      AnnotationPair<OrderBy> orderByHit =
              annotationHit(accessors, propertyName, OrderBy.class);

      if (orderByHit != null) {
         values.put("order_by", orderByHit.annotation().value());
      }

      if (annotationHit(accessors, propertyName, Id.class) != null) {
         values.put("id", Boolean.TRUE);
      }

      AnnotationPair<Content> contentHit =
              annotationHit(accessors, propertyName, Content.class);

      if (contentHit != null) {
         values.put("content", contentHit.annotation().type());
      }

      AnnotationPair<DBReturningValue> returningHit =
              annotationHit(accessors, propertyName, DBReturningValue.class);

      if (returningHit != null) {
         DBReturningValue rv = returningHit.annotation();

         values.put("column.returning.insert", rv.insert());
         values.put("column.returning.update", rv.update());
         values.put("column.returning.sequence_value", rv.sequenceValue());
      }

      AnnotationPair<SequenceGenerator> sequenceHit =
         annotationHit(accessors, propertyName, SequenceGenerator.class);

      if( sequenceHit != null )
      {
         SequenceGenerator sg = sequenceHit.annotation();
         values.put( "sequence.db_name", S.isNullOrEmpty(sg.sequenceName()) ? sg.name() : sg.sequenceName() );
      }

      AnnotationPair<EntityAttribute> entityAttributeHit =
              annotationHit(accessors, propertyName, EntityAttribute.class);

      if (entityAttributeHit != null) {
         EntityAttribute ea = entityAttributeHit.annotation();

         values.put("attribute.caseSensitiveSearchMode", ea.caseSensitiveSearchMode());

         if (ea.decimalStripTrailingZeros() && propertyType == BigDecimal.class) {
            values.put("attribute.decimalStripTrailingZeros", Boolean.TRUE);
         }

         values.put("attribute.useInLOVPredicate", ea.useInLOVPredicate());
      }
   }

   /** */
   private static <A extends Annotation> AnnotationPair<A> annotationHit(
        AccessorPair accessors,
        String propertyName,
        Class<A> annotationClass
   )
   {
      final List<AnnotationPair<A>> annotations = new ArrayList<>();

      if( accessors.readMethod() != null )
      {
         A annotation = accessors.readMethod().getAnnotation(annotationClass);
         if( annotation != null )
             annotations.add( new AnnotationPair<>(accessors.readMethod(), annotation) );
      }

      if( accessors.writeMethod() != null )
      {
         A annotation = accessors.writeMethod().getAnnotation( annotationClass );
         if( annotation != null )
             annotations.add(new AnnotationPair<>(accessors.writeMethod(), annotation));
      }

      if( annotations.isEmpty() )
          return null;

      // Проверка на дублирование аннотаций у setter и getter.
      // Если заданы одинаковые аннотации, но с разными параметрами - ошибка.
      // Возможно стоит считать что Getter забарывает Setter и писать алярм в лог!
      // Но пока так!
      AnnotationPair<A> first = annotations.get(0);

      for( int i = 1; i < annotations.size(); i++ )
      {
         AnnotationPair<A> other = annotations.get(i);

         if( !first.annotation().equals(other.annotation()) )
         {
            throw new EntityMetaDataException(
              Tags.PRODUCT_LABEL
                + "Conflicting @"
                + annotationClass.getSimpleName()
                + " annotations for property '"
                + propertyName
                + "': "
                + first.method().toGenericString()
                + " and "
                + other.method().toGenericString()
            );
         }
      }

      return first;
   }

   /** Поиск свойства в наборе.
     * Если не находим по имени самого свойства, то ищем по названию колонки таблицы БД.
     */
   private static <P> IEntityProperty<P, ?> findProperty( Map<String, IEntityProperty<P, ?>> propertiesMap, String key )
   {
      if( S.isNullOrEmpty(key) )
         return null;

      IEntityProperty<P, ?> property = propertiesMap.get(key);

      if( property != null )
          return property;

      for( IEntityProperty<P,?> ep : propertiesMap.values() )
      {
         if( ep.getColumnInfo() != null && key.equalsIgnoreCase( ep.getColumnInfo().getName() ) )
             return ep;
      }

      return null;
   }

   private static String[] resolveNames(Class<?> entityClass) {

      String[] names = new String[2];

      if (entityClass.isAnnotationPresent(Table.class)) {
         Table t = entityClass.getAnnotation(Table.class);
         names[0] = t.name();
      }

      if (entityClass.isAnnotationPresent(Entity.class)) {
         Entity t = entityClass.getAnnotation(Entity.class);

         if (!S.isNullOrEmpty(t.name())) {
            names[1] = t.name();
         }
      }

      return names;
   }

   /** */
   private static final class BeanProperty {

      private final String name;
      private final List<Method> getters = new ArrayList<>();
      private final List<Method> setters = new ArrayList<>();

      private BeanProperty(String name) {
         this.name = name;
      }
   }

   /** */
   private static final class AccessorPair extends Pair<Method,Method> {

      private static final AccessorPair EMPTY = new AccessorPair(null, null);

      private AccessorPair(Method readMethod, Method writeMethod) {
         super( readMethod, writeMethod );
      }

      private boolean isEmpty() {
         return first == null && second == null;
      }
      Method readMethod() {return first;}
      Method writeMethod(){return second;};
   }

   /** */
   private static final class AnnotationPair<A extends Annotation> extends Pair<Method,A> {

      private AnnotationPair(Method method, A annotation) {
         this.first  = method;
         this.second = annotation;
      }

      A annotation() { return second; }
      Method method(){ return first;  }
   }
}