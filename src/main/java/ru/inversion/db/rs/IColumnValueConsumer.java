package ru.inversion.db.rs;

/** */
@FunctionalInterface
public interface IColumnValueConsumer {
   /** */
   default void before() throws Exception { }
   default void beforeRow(int rowNum ) throws Exception {}
   void accept( int rowNum, int valueIndex, String columnKey, Object value ) throws Exception;
   default void afterRow(int rowNum ) throws Exception {}
   default void after( Exception e )  throws Exception{ }
}