package ru.inversion.db.marker;

public class ID2 implements Comparable<ID2> {

   final private long rowId;
   final private long aRowId;

   public ID2( long rowId, long aRowId )
   {
      this.rowId  = rowId;
      this.aRowId = aRowId;
   }

   public long rowId() {
      return rowId;
   }

   public long aRowId() {
      return aRowId;
   }

   @Override
   public int compareTo(ID2 o) {

      if( o == null )
          return 1;

      if( this == o )
          return 0;

      int compare = Long.compare( rowId, o.rowId);
      if( compare == 0 )
          compare = Long.compare( aRowId, o.aRowId );

      return compare;
   }

   @Override
   public boolean equals(Object o) {
      if( o == null || getClass() != o.getClass() )
         return false;
      ID2 id2 = (ID2) o;
      return rowId == id2.rowId && aRowId == id2.aRowId;
   }

   @Override
   public int hashCode() {
      int result = Long.hashCode(rowId);
      result = 31 * result + Long.hashCode(aRowId);
      return result;
   }

   public static ID2 of( long r, long a) {
      return new ID2( r, a );
   }

}
