package ru.inversion.tc.jdbc.event;

/** */
public final class JdbcConnectionEvent extends JdbcEvent
{
   private final boolean autoCommit;

   private JdbcConnectionEvent(
      Object source,
      EventPhase phase,
      boolean autoCommit,
      Throwable throwable
   )
   {
      super(
         source,
         EventType.CONNECTION_AUTOCOMMIT_SET,
         phase,
         throwable
      );

      this.autoCommit = autoCommit;
   }

   public boolean autoCommit()
   {
      return autoCommit;
   }

   public static JdbcConnectionEvent autoCommitSet( Object source, boolean autoCommit )
   {
      return new JdbcConnectionEvent(
         source,
         EventPhase.ON,
         autoCommit,
         null
      );
   }

   public static JdbcConnectionEvent autoCommitError( Object source, boolean autoCommit, Throwable throwable )
   {
      return new JdbcConnectionEvent(
         source,
         EventPhase.ERROR,
         autoCommit,
         throwable
      );
   }
}
