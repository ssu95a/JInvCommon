package ru.inversion.db;

import ru.inversion.db.dialect.SqlDialect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Унифицированное представление ошибки SQL, независимое от конкретного JDBC-драйвера.
 */
public final class SqlExceptionInfo {

   private final SqlDialect.DialectId dialect;

   private final String sqlState;
   private final int vendorCode;

   /**
    * Прикладной код управляемой ошибки:
    * RM001, RM002, ...
    */
   private final String applicationCode;

   /**
    * true - ошибка, специально сформированная бизнес-логикой приложения.
    */
   private final boolean managed;

   private final String message;
   private final String detail;
   private final String hint;

   private final String schemaName;
   private final String tableName;
   private final String columnName;
   private final String constraintName;

   /**
    * Прикладные атрибуты:
    */
   private final Map<String, String> attributes;


   private SqlExceptionInfo (Builder b )
   {
      dialect        = b.dialect;

      sqlState       = b.sqlState;
      vendorCode     = b.vendorCode;

      applicationCode= b.applicationCode;
      managed        = b.managed;

      message        = b.message;
      detail         = b.detail;
      hint           = b.hint;

      schemaName     = b.schemaName;
      tableName      = b.tableName;
      columnName     = b.columnName;
      constraintName = b.constraintName;

      attributes = Collections.unmodifiableMap( new LinkedHashMap<>(b.attributes) );
   }


   public static Builder builder( SqlDialect.DialectId dialect ) {
      return new Builder(dialect);
   }

   public SqlDialect.DialectId getDialect() {
      return dialect;
   }

   public String getSqlState() {
      return sqlState;
   }

   public int getVendorCode() {
      return vendorCode;
   }

   public String getApplicationCode() {
      return applicationCode;
   }

   public boolean isManaged() {
      return managed;
   }

   public String getMessage() {
      return message;
   }

   public String getDetail() {
      return detail;
   }

   public String getHint() {
      return hint;
   }

   public String getSchemaName() {
      return schemaName;
   }

   public String getTableName() {
      return tableName;
   }

   public String getColumnName() {
      return columnName;
   }

   public String getConstraintName() {
      return constraintName;
   }

   public Map<String, String> getAttributes() {
      return attributes;
   }

   public String getAttribute(String name) {
      return attributes.get(name);
   }

   /**
    * Наиболее информативное описание для пользователя/лога.
    */
   public String getDescription() {

      if(hint != null && !hint.trim().isEmpty() )
         return hint;

      if(detail != null && !detail.trim().isEmpty())
         return detail;

      return message;
   }


   @Override
   public String toString() {
      return "SqlExceptionInfo{" +
              "dialect=" + dialect +
              ", sqlState='" + sqlState + '\'' +
              ", vendorCode=" + vendorCode +
              ", applicationCode='" + applicationCode + '\'' +
              ", managed=" + managed +
              ", message='" + message + '\'' +
              ", detail='" + detail + '\'' +
              ", hint='" + hint + '\'' +
              ", attributes=" + attributes +
              '}';
   }


   public static final class Builder {

      private final SqlDialect.DialectId dialect;

      private String sqlState;
      private int vendorCode;

      private String applicationCode;
      private boolean managed;

      private String message;
      private String detail;
      private String hint;

      private String schemaName;
      private String tableName;
      private String columnName;
      private String constraintName;

      private final Map<String, String> attributes =
              new LinkedHashMap<>();


      private Builder(SqlDialect.DialectId dialect) {
         this.dialect = dialect;
      }


      public Builder sqlState(String v) {
         sqlState = v;
         return this;
      }

      public Builder vendorCode(int v) {
         vendorCode = v;
         return this;
      }

      public Builder applicationCode(String v) {
         applicationCode = v;
         return this;
      }

      public Builder managed(boolean v) {
         managed = v;
         return this;
      }

      public Builder message(String v) {
         message = v;
         return this;
      }

      public Builder detail(String v) {
         detail = v;
         return this;
      }

      public Builder hint(String v) {
         hint = v;
         return this;
      }

      public Builder schemaName(String v) {
         schemaName = v;
         return this;
      }

      public Builder tableName(String v) {
         tableName = v;
         return this;
      }

      public Builder columnName(String v) {
         columnName = v;
         return this;
      }

      public Builder constraintName(String v) {
         constraintName = v;
         return this;
      }

      public Builder attribute(String name, String value) {

         if (name != null && value != null)
            attributes.put(name, value);

         return this;
      }

      public Builder attributes(Map<String, String> values) {

         if (values != null)
            attributes.putAll(values);

         return this;
      }

      public SqlExceptionInfo build() {
         return new SqlExceptionInfo(this);
      }
   }
}