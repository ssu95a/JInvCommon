package ru.inversion.db.rs;

import ru.inversion.db.rs.ColumnBinding.MissingColumnStrategy;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * Builds stable ResultSet column bindings.
 *
 * Column lookup is case-insensitive.
 * Lookup uses columnLabel first, then columnName.
 */
class ResultSetColumnBindings {

   private ResultSetColumnBindings() {
   }

   /** */
   static ColumnBinding[] create( ResultSet rs, MissingColumnStrategy missingColumnStrategy, Collection<String> columns ) throws SQLException
   {
      String[] array = columns == null ? null : columns.toArray( new String[0] );
      return create( rs, missingColumnStrategy, array );
   }

   /** */
   static ColumnBinding[] create( ResultSet rs, MissingColumnStrategy missingColumnStrategy, String... columns ) throws SQLException
   {
      Checks.Require.object( rs, "rs" );

      final MissingColumnStrategy strategy = missingColumnStrategy == null ? MissingColumnStrategy.ERROR : missingColumnStrategy;

      final ResultSetMetaData metaData = rs.getMetaData();

      final int columnCount = metaData.getColumnCount();

      final Map<String, Integer> indexByName = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );

      final List<String> availableColumns = new ArrayList<>( columnCount );

      for( int i = 1; i <= columnCount; i++ )
      {
         String columnKey  = getColumnKey( metaData, i );
         String columnName = metaData.getColumnName(i);

         if( !S.isNullOrEmpty(columnKey) )
         {
            indexByName.putIfAbsent( columnKey, i );
            availableColumns.add( columnKey );
         }

         if( !S.isNullOrEmpty( columnName ) )
             indexByName.putIfAbsent( columnName, i );
      }

      // columns == null / empty means:
      // use all ResultSet columns in their metadata order.
      if( columns == null || columns.length == 0 )
      {
         ColumnBinding[] bindings = new ColumnBinding[columnCount];

         for( int i = 1; i <= columnCount; i++ )
              bindings[i - 1] = ColumnBinding.exists( getColumnKey( metaData, i ), i );

         return bindings;
      }

      List<ColumnBinding> bindings = new ArrayList<>(columns.length);

      for( String column : columns )
      {
         if( S.isNullOrEmpty(column) )
             throw new SQLException( "Column name is empty. Requested columns: " + Arrays.toString(columns) );

         Integer columnIndex = indexByName.get(column);

         if( columnIndex != null )
         {
            bindings.add(ColumnBinding.exists(column, columnIndex));
            continue;
         }

         switch( strategy )
         {
            case ERROR:
               throw new SQLException( "Column '" + column + "' not found in ResultSet. Available columns: " + availableColumns);
            case SKIP:
               continue;
            case NULL_VALUE:
               bindings.add( ColumnBinding.missing(column) );
               continue;
            default:
               throw new SQLException( "Unsupported missing column strategy: " + strategy );
         }
      }

      return bindings.toArray( new ColumnBinding[0] );
   }

   /** */
   private static String getColumnKey( ResultSetMetaData metaData, int columnIndex ) throws SQLException
   {
      String columnKey = metaData.getColumnLabel(columnIndex);

      if( S.isNullOrEmpty(columnKey) )
          columnKey = metaData.getColumnName(columnIndex);

      return columnKey;
   }
}