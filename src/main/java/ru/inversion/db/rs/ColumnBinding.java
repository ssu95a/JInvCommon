package ru.inversion.db.rs;


/**
 * Binding между именем колонки в ResultSet и его индексом
 * также признак, что колонка с такими номером или именем
 * отсутствует в ResultSet
 */
public class ColumnBinding {

   /** Как обрабатывать отсутствующие колонки */
   public enum MissingColumnStrategy {
      /** Выбрасывать исключение */
      ERROR,
      /** Пропускать */
      SKIP,
      /** Заменять на null */
      NULL_VALUE
   }

   /** Имя колонки */
   final String  columnKey;
   /** Индекс, с 1 */
   final int     columnIndex;
   /** Признак, что колонка отсутствует */
   final boolean missing;

   private ColumnBinding( String columnKey, int columnIndex, boolean missing )
   {
      this.columnKey   = columnKey;
      this.columnIndex = columnIndex;
      this.missing     = missing;
   }

   /** */
   static ColumnBinding exists( String columnKey, int columnIndex ) {
      return new ColumnBinding( columnKey, columnIndex, false );
   }

   /** */
   static ColumnBinding missing(String columnKey) {
      return new ColumnBinding(columnKey, -1, true);
   }
}