package ru.inversion.db.rs;

import ru.inversion.db.rs.ColumnBinding.MissingColumnStrategy;
import ru.inversion.utils.Checks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;

public class ResultSetRowsWalker {

      private final IColumnsRSRowStreamer streamer;

      private ResultSetRowsWalker( IColumnsRSRowStreamer streamer ) {
         this.streamer = Objects.requireNonNull(streamer, "'streamer' is null");
      }

      public String[] getColumns() {
         return streamer.getColumns();
      }

      public ResultSet getResultSet() {
         return streamer.getResultSet();
      }

      public int walk( IColumnValueConsumer consumer ) throws Exception {

         Checks.Require.object( consumer, "consumer" );

         int rowNum = 0;
         Exception failure = null;

         consumer.before();

         try( ResultSet rs = streamer.getResultSet() ) {

            while( rs.next() )
            {
               consumer.beforeRow(rowNum);

               streamer.forEachValue( rowNum, consumer);

               consumer.afterRow(rowNum);

               rowNum++;
            }

            return rowNum;
         }
         catch( Exception ex ) {
            failure = ex;
            throw ex;
         }
         finally {

            try {
               consumer.after(failure);
            }
            catch( Exception afterEx ) {
               if( failure != null ) {
                   failure.addSuppressed(afterEx);
               }
               else
               {
                  throw afterEx;
               }
            }
         }
      }

   /** */
   public static ResultSetRowsWalker of( ResultSet rs, Collection<String> columns ) throws SQLException
   {
      return of( rs, MissingColumnStrategy.ERROR, columns );
   }

   /** */
   public static ResultSetRowsWalker of (
           ResultSet rs,
           MissingColumnStrategy missingColumnStrategy,
           Collection<String> columns
   )
           throws SQLException
   {
      return new ResultSetRowsWalker (
         ResultSetRows.columns( rs, missingColumnStrategy, columns )
      );
   }

}