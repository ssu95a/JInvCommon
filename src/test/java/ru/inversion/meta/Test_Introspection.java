
package ru.inversion.meta;

import org.junit.jupiter.api.Test;
import ru.inversion.meta.impl.EntityMetadata;
import ru.inversion.meta.impl.EntityMetadataIntrospector;
import ru.inversion.db.entity.ProxyColumns;
import ru.inversion.db.entity.ProxyFor;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Transient;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Introspection {

   // ---------------------------------------------------------------------
   // 1. Simple POJO
   // ---------------------------------------------------------------------

   @Test
   void test_simple_pojo_getter_setter() {
      EntityMetadata<SimplePojo> md =
              EntityMetadataIntrospector.introspect(SimplePojo.class);

      IEntityProperty<SimplePojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertEquals("value", p.getPropertyName());
      assertEquals(String.class, p.getType());

      SimplePojo pojo = new SimplePojo();

      p.invokeSetter(pojo, "abc");

      assertEquals("abc", pojo.getValue());
      assertEquals("abc", p.invokeGetter(pojo));
   }

   public static class SimplePojo {

      private String value;

      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 2. Original bug: property declared through interface
   // ---------------------------------------------------------------------

   @Test
   void test_interface_getter_and_concrete_getter_setter_original_bug() {
      EntityMetadata<InterfacePojo> md =
              EntityMetadataIntrospector.introspect(InterfacePojo.class);

      IEntityProperty<InterfacePojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertEquals(String.class, p.getType());

      InterfacePojo pojo = new InterfacePojo();

      p.invokeSetter(pojo, "abc");

      assertEquals("abc", pojo.getValue());
      assertEquals("abc", p.invokeGetter(pojo));
   }

   public interface HasValue {
      CharSequence getValue();
   }

   public static class InterfacePojo implements HasValue {

      private String value;

      @Override
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 3. Generic interface: bridge/synthetic methods must not break pairing
   // ---------------------------------------------------------------------

   @Test
   void test_generic_interface_getter_bridge_methods_ignored() {
      EntityMetadata<GenericPojo> md =
              EntityMetadataIntrospector.introspect(GenericPojo.class);

      IEntityProperty<GenericPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertEquals(String.class, p.getType());

      GenericPojo pojo = new GenericPojo();

      p.invokeSetter(pojo, "abc");

      assertEquals("abc", pojo.getValue());
   }

   public interface GenericValue<T> {
      T getValue();
   }

   public static class GenericPojo implements GenericValue<String> {

      private String value;

      @Override
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 4. Boolean property: isX + setX
   // ---------------------------------------------------------------------

   @Test
   void test_boolean_is_getter() {
      EntityMetadata<BooleanPojo> md =
              EntityMetadataIntrospector.introspect(BooleanPojo.class);

      IEntityProperty<BooleanPojo, ?> p = md.getProperty("active");

      assertNotNull(p);
      assertEquals(boolean.class, p.getType());

      BooleanPojo pojo = new BooleanPojo();

      p.invokeSetter(pojo, true);

      assertTrue(pojo.isActive());
      assertEquals(Boolean.TRUE, p.invokeGetter(pojo));
   }

   public static class BooleanPojo {

      private boolean active;

      public boolean isActive() {
         return active;
      }

      public void setActive(boolean active) {
         this.active = active;
      }
   }

   // ---------------------------------------------------------------------
   // 5. Boolean property: both isX and getX exist
   // ---------------------------------------------------------------------

   @Test
   void test_boolean_property_with_is_and_get() {
      EntityMetadata<BooleanIsAndGetPojo> md =
              EntityMetadataIntrospector.introspect(BooleanIsAndGetPojo.class);

      IEntityProperty<BooleanIsAndGetPojo, ?> p = md.getProperty("active");

      assertNotNull(p);
      assertEquals(boolean.class, p.getType());

      BooleanIsAndGetPojo pojo = new BooleanIsAndGetPojo();

      p.invokeSetter(pojo, true);

      assertTrue(pojo.isActive());
      assertEquals(Boolean.TRUE, p.invokeGetter(pojo));
   }

   public static class BooleanIsAndGetPojo {

      private boolean active;

      public boolean isActive() {
         return active;
      }

      public boolean getActive() {
         return active;
      }

      public void setActive(boolean active) {
         this.active = active;
      }
   }

   // ---------------------------------------------------------------------
   // 6. Overloaded setters: choose most specific compatible setter
   // ---------------------------------------------------------------------

   @Test
   void test_overloaded_setters_choose_most_specific() {
      EntityMetadata<OverloadedSetterPojo> md =
              EntityMetadataIntrospector.introspect(OverloadedSetterPojo.class);

      IEntityProperty<OverloadedSetterPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertEquals(String.class, p.getType());

      OverloadedSetterPojo pojo = new OverloadedSetterPojo();

      p.invokeSetter(pojo, "abc");

      assertEquals("String:abc", pojo.getValue());
   }

   public static class OverloadedSetterPojo {

      private String value;

      public String getValue() {
         return value;
      }

      public void setValue(Object value) {
         this.value = "Object:" + value;
      }

      public void setValue(CharSequence value) {
         this.value = "CharSequence:" + value;
      }

      public void setValue(String value) {
         this.value = "String:" + value;
      }
   }

   // ---------------------------------------------------------------------
   // 7. Incompatible setter must not be selected
   // ---------------------------------------------------------------------

   @Test
   void test_incompatible_setter_is_not_selected() {
      EntityMetadata<IncompatibleSetterPojo> md =
              EntityMetadataIntrospector.introspect(IncompatibleSetterPojo.class);

      IEntityProperty<IncompatibleSetterPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertEquals(CharSequence.class, p.getType());

      IncompatibleSetterPojo pojo = new IncompatibleSetterPojo();

      assertEquals("initial", p.invokeGetter(pojo));

      assertTrue(
              p.isReadOnly(),
              "Setter setValue(String) must not be paired with getter CharSequence getValue()"
      );
   }

   public static class IncompatibleSetterPojo {

      public CharSequence getValue() {
         return "initial";
      }

      public void setValue(String value) {
         throw new AssertionError("This setter must not be selected");
      }
   }

   // ---------------------------------------------------------------------
   // 8. Write-only non-column property must be skipped
   // ---------------------------------------------------------------------

   @Test
   void test_write_only_non_column_property_is_skipped() {
      EntityMetadata<WriteOnlyPojo> md =
              EntityMetadataIntrospector.introspect(WriteOnlyPojo.class);

      assertNull(md.getProperty("value"));
      assertFalse(md.getPropertiesMap().containsKey("value"));
   }

   public static class WriteOnlyPojo {

      public void setValue(String value) {
      }
   }

   // ---------------------------------------------------------------------
   // 9. @Column on getter
   // ---------------------------------------------------------------------

   @Test
   void test_column_on_getter() {
      EntityMetadata<ColumnOnGetterPojo> md =
              EntityMetadataIntrospector.introspect(ColumnOnGetterPojo.class);

      IEntityProperty<ColumnOnGetterPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertNotNull(p.getColumnInfo());
      assertEquals("VALUE_COL", p.getColumnInfo().getName());
      assertSame(p, md.getColumnsMap().get("VALUE_COL"));
   }

   public static class ColumnOnGetterPojo {

      private String value;

      @Column(name = "VALUE_COL")
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 10. @Column on setter
   // ---------------------------------------------------------------------

   @Test
   void test_column_on_setter() {
      EntityMetadata<ColumnOnSetterPojo> md =
              EntityMetadataIntrospector.introspect(ColumnOnSetterPojo.class);

      IEntityProperty<ColumnOnSetterPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertNotNull(p.getColumnInfo());
      assertEquals("VALUE_COL", p.getColumnInfo().getName());
      assertSame(p, md.getColumnsMap().get("VALUE_COL"));
   }

   public static class ColumnOnSetterPojo {

      private String value;

      public String getValue() {
         return value;
      }

      @Column(name = "VALUE_COL")
      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 11. Same @Column on getter and setter is OK
   // ---------------------------------------------------------------------

   @Test
   void test_same_column_on_getter_and_setter_is_ok() {
      EntityMetadata<SameColumnOnGetterSetterPojo> md =
              EntityMetadataIntrospector.introspect(SameColumnOnGetterSetterPojo.class);

      IEntityProperty<SameColumnOnGetterSetterPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertNotNull(p.getColumnInfo());
      assertEquals("VALUE_COL", p.getColumnInfo().getName());
   }

   public static class SameColumnOnGetterSetterPojo {

      private String value;

      @Column(name = "VALUE_COL")
      public String getValue() {
         return value;
      }

      @Column(name = "VALUE_COL")
      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 12. Conflicting @Column on getter/setter must fail
   // ---------------------------------------------------------------------

   @Test
   void test_conflicting_column_on_getter_and_setter_fails() {
      EntityMetaDataException ex = assertThrows(
              EntityMetaDataException.class,
              () -> EntityMetadataIntrospector.introspect(ConflictingColumnPojo.class)
      );

      assertTrue(ex.getMessage().contains("Conflicting @Column"));
      assertTrue(ex.getMessage().contains("value"));
   }

   public static class ConflictingColumnPojo {

      private String value;

      @Column(name = "VALUE_GET")
      public String getValue() {
         return value;
      }

      @Column(name = "VALUE_SET")
      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 13. Annotation on unselected interface getter must be ignored
   // ---------------------------------------------------------------------

   @Test
   void test_annotation_on_unselected_interface_getter_is_ignored() {
      EntityMetadata<InterfaceAnnotationIgnoredPojo> md =
              EntityMetadataIntrospector.introspect(InterfaceAnnotationIgnoredPojo.class);

      IEntityProperty<InterfaceAnnotationIgnoredPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertNull(
              p.getColumnInfo(),
              "Interface @Column must be ignored when selected getter is concrete POJO getter without @Column"
      );
   }

   public interface AnnotatedInterfaceValue {
      @Column(name = "IFACE_VALUE")
      CharSequence getValue();
   }

   public static class InterfaceAnnotationIgnoredPojo implements AnnotatedInterfaceValue {

      private String value;

      @Override
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 14. Concrete getter annotation wins because only selected accessor pair is inspected
   // ---------------------------------------------------------------------

   @Test
   void test_concrete_getter_column_is_used_even_if_interface_has_different_column() {
      EntityMetadata<ConcreteAnnotationPojo> md =
              EntityMetadataIntrospector.introspect(ConcreteAnnotationPojo.class);

      IEntityProperty<ConcreteAnnotationPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertNotNull(p.getColumnInfo());
      assertEquals("POJO_VALUE", p.getColumnInfo().getName());
      assertNull(md.getColumnsMap().get("IFACE_VALUE"));
   }

   public interface InterfaceWithDifferentColumn {
      @Column(name = "IFACE_VALUE")
      CharSequence getValue();
   }

   public static class ConcreteAnnotationPojo implements InterfaceWithDifferentColumn {

      private String value;

      @Override
      @Column(name = "POJO_VALUE")
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 15. @Id must be collected
   // ---------------------------------------------------------------------

   @Test
   void test_id_property() {
      EntityMetadata<IdPojo> md =
              EntityMetadataIntrospector.introspect(IdPojo.class);

      IEntityProperty<IdPojo, ?> p = md.getProperty("id");

      assertNotNull(p);
      assertTrue(p.isId());
      assertEquals(1, md.getIDList().size());
      assertSame(p, md.getIDList().get(0));
   }

   public static class IdPojo {

      private Long id;

      @Id
      @Column(name = "ID")
      public Long getId() {
         return id;
      }

      public void setId(Long id) {
         this.id = id;
      }
   }

   // ---------------------------------------------------------------------
   // 16. Case-insensitive column map
   // ---------------------------------------------------------------------

   @Test
   void test_column_map_is_case_insensitive() {
      EntityMetadata<ColumnOnGetterPojo> md =
              EntityMetadataIntrospector.introspect(ColumnOnGetterPojo.class);

      IEntityProperty<ColumnOnGetterPojo, ?> p1 = md.getColumnsMap().get("VALUE_COL");
      IEntityProperty<ColumnOnGetterPojo, ?> p2 = md.getColumnsMap().get("value_col");
      IEntityProperty<ColumnOnGetterPojo, ?> p3 = md.getColumnsMap().get("Value_Col");

      assertNotNull(p1);
      assertSame(p1, p2);
      assertSame(p1, p3);
   }

   // ---------------------------------------------------------------------
   // 17. @Column + @Transient keeps legacy behavior: map key becomes column name
   // ---------------------------------------------------------------------

   @Test
   void test_column_plus_transient_legacy_key_behavior() {
      EntityMetadata<ColumnTransientPojo> md =
              EntityMetadataIntrospector.introspect(ColumnTransientPojo.class);

      assertFalse(
              md.getPropertiesMap().containsKey("rowNumber"),
              "Legacy behavior: @Column + @Transient property is stored by column name"
      );

      IEntityProperty<ColumnTransientPojo, ?> p = md.getProperty("RN");

      assertNotNull(p);
      assertEquals("RN", p.getPropertyName());
      assertNull(
              p.getColumnInfo(),
              "EntityProperty marks @Transient property as non-column even if @Column exists"
      );
   }

   public static class ColumnTransientPojo {

      private Integer rowNumber;

      @Column(name = "RN")
      @Transient
      public Integer getRowNumber() {
         return rowNumber;
      }

      public void setRowNumber(Integer rowNumber) {
         this.rowNumber = rowNumber;
      }
   }

   // ---------------------------------------------------------------------
   // 18. Non-column calculated/transient getter is present as transient property
   // ---------------------------------------------------------------------

   @Test
   void test_calculated_non_column_getter_is_added_as_transient_property() {
      EntityMetadata<CalculatedPojo> md =
              EntityMetadataIntrospector.introspect(CalculatedPojo.class);

      IEntityProperty<CalculatedPojo, ?> p = md.getProperty("displayName");

      assertNotNull(p);
      assertNull(p.getColumnInfo());

      CalculatedPojo pojo = new CalculatedPojo();
      pojo.setFirstName("John");
      pojo.setLastName("Smith");

      assertEquals("John Smith", p.invokeGetter(pojo));
   }

   public static class CalculatedPojo {

      private String firstName;
      private String lastName;

      @Column(name = "FIRST_NAME")
      public String getFirstName() {
         return firstName;
      }

      public void setFirstName(String firstName) {
         this.firstName = firstName;
      }

      @Column(name = "LAST_NAME")
      public String getLastName() {
         return lastName;
      }

      public void setLastName(String lastName) {
         this.lastName = lastName;
      }

      public String getDisplayName() {
         return firstName + " " + lastName;
      }
   }

   // ---------------------------------------------------------------------
   // 19. @ProxyFor
   // ---------------------------------------------------------------------

   @Test
   void test_proxy_for() {
      EntityMetadata<ProxyForPojo> md =
              EntityMetadataIntrospector.introspect(ProxyForPojo.class);

      IEntityProperty<ProxyForPojo, ?> code = md.getProperty("code");
      IEntityProperty<ProxyForPojo, ?> name = md.getProperty("name");

      assertNotNull(code);
      assertNotNull(name);

      assertNull(code.getProxyFor());
      assertSame(code, name.getProxyFor());
   }

   public static class ProxyForPojo {

      private String code;
      private String name;

      @Column(name = "CODE")
      public String getCode() {
         return code;
      }

      public void setCode(String code) {
         this.code = code;
      }

      @ProxyFor(columnName = "CODE")
      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }
   }

   // ---------------------------------------------------------------------
   // 20. @ProxyColumns
   // ---------------------------------------------------------------------

   @Test
   void test_proxy_columns() {
      EntityMetadata<ProxyColumnsPojo> md =
              EntityMetadataIntrospector.introspect(ProxyColumnsPojo.class);

      IEntityProperty<ProxyColumnsPojo, ?> display = md.getProperty("display");
      IEntityProperty<ProxyColumnsPojo, ?> code = md.getProperty("code");

      assertNotNull(display);
      assertNotNull(code);

      assertSame(display, code.getProxyFor());
   }

   public static class ProxyColumnsPojo {

      private String code;
      private String display;

      @Column(name = "CODE")
      public String getCode() {
         return code;
      }

      public void setCode(String code) {
         this.code = code;
      }

      @ProxyColumns(columnNames = {"CODE"})
      public String getDisplay() {
         return display;
      }

      public void setDisplay(String display) {
         this.display = display;
      }
   }

   // ---------------------------------------------------------------------
   // 21. Empty/invalid @Column name is ignored, same as legacy logic
   // ---------------------------------------------------------------------

   @Test
   void test_empty_column_name_is_ignored_as_column() {
      EntityMetadata<EmptyColumnNamePojo> md =
              EntityMetadataIntrospector.introspect(EmptyColumnNamePojo.class);

      IEntityProperty<EmptyColumnNamePojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertNull(p.getColumnInfo());
      assertTrue(md.getColumnsMap().isEmpty());
   }

   public static class EmptyColumnNamePojo {

      private String value;

      @Column(name = "")
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 22. Read-only calculated property is OK
   // ---------------------------------------------------------------------

   @Test
   void test_read_only_calculated_property_is_ok() {
      EntityMetadata<ReadOnlyCalculatedPojo> md =
              EntityMetadataIntrospector.introspect(ReadOnlyCalculatedPojo.class);

      IEntityProperty<ReadOnlyCalculatedPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertTrue(p.isReadOnly());

      ReadOnlyCalculatedPojo pojo = new ReadOnlyCalculatedPojo();

      assertEquals("readonly", p.invokeGetter(pojo));
   }

   public static class ReadOnlyCalculatedPojo {

      public String getValue() {
         return "readonly";
      }
   }

   // ---------------------------------------------------------------------
   // 23. Getter from class should be preferred over interface getter
   // ---------------------------------------------------------------------

   @Test
   void test_concrete_getter_is_preferred_over_interface_getter() {
      EntityMetadata<ConcreteGetterPreferredPojo> md =
              EntityMetadataIntrospector.introspect(ConcreteGetterPreferredPojo.class);

      IEntityProperty<ConcreteGetterPreferredPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertEquals(String.class, p.getType());

      ConcreteGetterPreferredPojo pojo = new ConcreteGetterPreferredPojo();

      p.invokeSetter(pojo, "abc");

      assertEquals("abc", p.invokeGetter(pojo));
   }

   public interface ParentGetter {
      Object getValue();
   }

   public static class ConcreteGetterPreferredPojo implements ParentGetter {

      private String value;

      @Override
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }
   }

   // ---------------------------------------------------------------------
   // 24. Compatible broad setter is allowed
   // ---------------------------------------------------------------------

   @Test
   void test_broad_setter_is_compatible() {
      EntityMetadata<BroadSetterPojo> md =
              EntityMetadataIntrospector.introspect(BroadSetterPojo.class);

      IEntityProperty<BroadSetterPojo, ?> p = md.getProperty("value");

      assertNotNull(p);
      assertEquals(String.class, p.getType());

      BroadSetterPojo pojo = new BroadSetterPojo();

      p.invokeSetter(pojo, "abc");

      assertEquals("abc", pojo.getValue());
   }

   public static class BroadSetterPojo {

      private String value;

      public String getValue() {
         return value;
      }

      public void setValue(CharSequence value) {
         this.value = value == null ? null : value.toString();
      }
   }

   // ---------------------------------------------------------------------
   // 25. Smoke: metadata object contains stable maps
   // ---------------------------------------------------------------------

   @Test
   void test_metadata_maps_are_stable() {
      EntityMetadata<IdPojo> md =
              EntityMetadataIntrospector.introspect(IdPojo.class);

      assertNotNull(md.getPropertiesMap());
      assertNotNull(md.getColumnsMap());
      assertNotNull(md.getIDList());

      assertTrue(md.getPropertiesMap().containsKey("id"));
      assertTrue(md.getColumnsMap().containsKey("ID"));
      assertEquals(1, md.getIDList().size());
   }

   public static class ProxyUppercasePojo {

      private Integer N_SUIT_TYPE;
      private String C_TYPE;

      @Column(name = "N_SUIT_TYPE")
      public Integer getN_SUIT_TYPE() {
         return N_SUIT_TYPE;
      }

      public void setN_SUIT_TYPE(Integer N_SUIT_TYPE) {
         this.N_SUIT_TYPE = N_SUIT_TYPE;
      }

      @Transient
      @ProxyFor(columnName = "N_SUIT_TYPE")
      public String getC_TYPE() {
         return C_TYPE;
      }

      public void setC_TYPE(String C_TYPE) {
         this.C_TYPE = C_TYPE;
      }
   }

   @Test
   void test_uppercase_underscore_property_name_is_kept_as_legacy_name() {
      EntityMetadata<ProxyUppercasePojo> md =
              EntityMetadataIntrospector.introspect(ProxyUppercasePojo.class);

      IEntityProperty<ProxyUppercasePojo, ?> target = md.getProperty("N_SUIT_TYPE");
      IEntityProperty<ProxyUppercasePojo, ?> proxy = md.getProperty("C_TYPE");

      assertNotNull(target);
      assertNotNull(proxy);

      assertEquals("C_TYPE", proxy.getPropertyName());
      assertSame(target, proxy.getProxyFor());
   }
}