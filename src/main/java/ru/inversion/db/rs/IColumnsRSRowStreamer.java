package ru.inversion.db.rs;

import java.sql.ResultSet;

/** */
public interface IColumnsRSRowStreamer {

   /** ResultSet по которому идет выгрузка */
   ResultSet getResultSet();

   /** Фактические колонки streamer-а в порядке выдачи значений.*/
   String[] getColumns();

   /** Пройти по значениям текущей строки ResultSet. */
   void forEachValue( int rowNum, IColumnValueConsumer consumer ) throws Exception;
}
