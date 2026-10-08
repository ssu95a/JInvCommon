package ru.inversion.db.rs;

import ru.inversion.dataset.IRSRowMapper;
import ru.inversion.db.rs.ColumnBinding.MissingColumnStrategy;
import ru.inversion.utils.Checks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static ru.inversion.db.rs.ColumnBinding.MissingColumnStrategy.ERROR;


public final class ResultSetRows {

   private ResultSetRows() {
   }

   /** */
   public static IColumnsRSRowStreamer columns( ResultSet rs, Collection<String> columns ) throws SQLException {
      return columns( rs, ERROR, columns );
   }

   /** */
   public static IColumnsRSRowStreamer columns( ResultSet rs, String... columns ) throws SQLException {
      return columns( rs, ERROR, columns );
   }

   /** */
   public static IColumnsRSRowStreamer columns( ResultSet rs, MissingColumnStrategy missingColumnStrategy, Collection<String> columns ) throws SQLException
   {
      return new DefaultColumnsRSRowStreamer( rs, missingColumnStrategy, columns );
   }

   /** */
   public static IColumnsRSRowStreamer columns( ResultSet rs, MissingColumnStrategy missingColumnStrategy, String... columns ) throws SQLException {
      return new DefaultColumnsRSRowStreamer( rs, missingColumnStrategy, columns );
   }

   /** */
   public static IRSRowMapper<Object[]> objectArray( ResultSet rs, Collection<String> columns ) throws SQLException {
      return new ObjectArrayRSRowMapper(rs, columns);
   }

   /** */
   public static IRSRowMapper<Object[]> objectArray( ResultSet rs, String... columns ) throws SQLException {
      return new ObjectArrayRSRowMapper(rs, columns);
   }

   /** */
   public static IRSRowMapper<String[]> stringArray( ResultSet rs, Collection<String> columns ) throws SQLException {
      return new StringArrayRSRowMapper(rs, columns);
   }

   /** */
   public static IRSRowMapper<String[]> stringArray( ResultSet rs, String... columns ) throws SQLException {
      return new StringArrayRSRowMapper(rs, columns);
   }

   /** */
   public static IRSRowMapper<Map<String, Object>> objectMap( ResultSet rs, Collection<String> columns ) throws SQLException {
      return new ObjectMapRSRowMapper(rs, columns);
   }

   /** */
   public static IRSRowMapper<Map<String, Object>> objectMap( ResultSet rs, String... columns ) throws SQLException {
      return new ObjectMapRSRowMapper(rs, columns);
   }

   /** */
   private static class BaseColumnsRSRowMapper {

      protected final ResultSet rs;
      protected final ColumnBinding[] bindings;

      private final String[] columnKeys;

      private BaseColumnsRSRowMapper( ResultSet rs, Collection<String> columns ) throws SQLException {
         this( rs, ERROR, columns );
      }

      private BaseColumnsRSRowMapper( ResultSet rs, String... columns ) throws SQLException {
         this( rs, ERROR, columns );
      }

      /** */
      private BaseColumnsRSRowMapper( ResultSet rs, MissingColumnStrategy missingColumnStrategy, Collection<String> columns ) throws SQLException {
         this.rs         = Checks.Require.object( rs, "rs" );
         this.bindings   = ResultSetColumnBindings.create( rs, missingColumnStrategy, columns );
         this.columnKeys = makeColumnKeys(bindings);
      }

      /** */
      private BaseColumnsRSRowMapper( ResultSet rs, MissingColumnStrategy missingColumnStrategy, String... columns ) throws SQLException {
         this.rs         = Checks.Require.object( rs, "rs" );
         this.bindings   = ResultSetColumnBindings.create( rs, missingColumnStrategy, columns);
         this.columnKeys = makeColumnKeys(bindings);
      }

      /** */
      public ResultSet getResultSet() {
         return rs;
      }

      /** */
      public String[] getColumns() {
         return columnKeys;
      }

      /** */
      private static String[] makeColumnKeys( ColumnBinding[] bindings )
      {
         String[] keys = new String[bindings.length];

         for( int i = 0; i < bindings.length; i++ )
              keys[i] = bindings[i].columnKey;

         return keys;
      }

   }

   /** */
   private static class DefaultColumnsRSRowStreamer extends BaseColumnsRSRowMapper implements IColumnsRSRowStreamer {

      private DefaultColumnsRSRowStreamer( ResultSet rs, MissingColumnStrategy missingColumnStrategy, Collection<String> columns ) throws SQLException {
         super( rs, missingColumnStrategy, columns );
      }

      private DefaultColumnsRSRowStreamer( ResultSet rs, MissingColumnStrategy missingColumnStrategy, String... columns ) throws SQLException {
         super( rs, missingColumnStrategy, columns );
      }

      /** */
      @Override
      public void forEachValue( int rowNum, IColumnValueConsumer consumer ) throws Exception {

         Checks.Require.object( consumer, "consumer" );

         for( int i = 0; i < bindings.length; i++) {

              ColumnBinding binding = bindings[i];
              Object value = binding.missing ? null : rs.getObject( binding.columnIndex);

              consumer.accept( rowNum, i, binding.columnKey, value );
         }//end for
      }

   }

   /** */
   private static final class ObjectArrayRSRowMapper extends BaseColumnsRSRowMapper implements IRSRowMapper<Object[]> {

      private ObjectArrayRSRowMapper( ResultSet rs, Collection<String> columns ) throws SQLException {
         super(rs, columns);
      }

      private ObjectArrayRSRowMapper( ResultSet rs, String... columns ) throws SQLException {
         super(rs, columns);
      }

      @Override
      public Object[] mapRow( int rowNum ) throws SQLException {

         Object[] row = new Object[bindings.length];

         for( int i = 0; i < bindings.length; i++ )
         {
            ColumnBinding binding = bindings[i];
            row[i] = binding.missing ? null : rs.getObject(binding.columnIndex);
         }

         return row;
      }

      @Override
      public ResultSet getResultSet() {
         return rs;
      }
   }

   /** */
   private static final class StringArrayRSRowMapper extends BaseColumnsRSRowMapper implements IRSRowMapper<String[]> {

      private StringArrayRSRowMapper( ResultSet rs, Collection<String> columns ) throws SQLException {
         super( rs, columns );
      }

      private StringArrayRSRowMapper( ResultSet rs, String... columns ) throws SQLException {
         super( rs, columns );
      }

      @Override
      public String[] mapRow(int rowNum) throws SQLException {

         String[] row = new String[bindings.length];

         for( int i = 0; i < bindings.length; i++ )
         {
            ColumnBinding binding = bindings[i];
            row[i] = binding.missing ? null : rs.getString( binding.columnIndex );
         }
         return row;
      }

      @Override
      public ResultSet getResultSet() {
         return rs;
      }
   }

   /** */
   private static final class ObjectMapRSRowMapper extends BaseColumnsRSRowMapper implements IRSRowMapper<Map<String, Object>> {

      private ObjectMapRSRowMapper( ResultSet rs, Collection<String> columns ) throws SQLException {
         super( rs, columns );
      }

      private ObjectMapRSRowMapper( ResultSet rs, String... columns ) throws SQLException {
         super( rs, columns );
      }

      @Override
      public Map<String, Object> mapRow(int rowNum) throws SQLException {

         Map<String, Object> row = new LinkedHashMap<>();

         for( ColumnBinding binding : bindings )
         {
            Object value = binding.missing ? null : rs.getObject(binding.columnIndex);
            row.put(binding.columnKey, value);
         }

         return row;
      }

      @Override
      public ResultSet getResultSet() {
         return rs;
      }
   }
}