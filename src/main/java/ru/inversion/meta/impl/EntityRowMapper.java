package ru.inversion.meta.impl;

import ru.inversion.dataset.IRSRowMapper;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.utils.Checks;

import java.lang.ref.WeakReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * <h5>IRowMapper facade.</h5>
 * <br>
 * Кэширует IRSRowMapper на конкретный ResultSet,
 * чтобы не пересобирать column bindings на каждую строку.
 */
final class EntityRowMapper<T> implements IRowMapper<T> {

   private final IEntityMetaData<T> entityMetadata;

   private WeakReference<IRSRowMapper<T>> weakRowMapper;

   EntityRowMapper( IEntityMetaData<T> entityMetadata ) {
      this.entityMetadata = Checks.Require.object( entityMetadata, "entityMetadata");
   }

   @Override
   public T mapRow(ResultSet rs, int rowNum) throws SQLException {

      IRSRowMapper<T> rowMapper = null;

      if( weakRowMapper != null )
      {
         rowMapper = weakRowMapper.get();

         if( rowMapper != null && rowMapper.getResultSet() != rs) {
             rowMapper = null;
         }
      }

      if(rowMapper == null)
      {
         rowMapper = entityMetadata.createRSRowMapper(rs);
         weakRowMapper = new WeakReference<>(rowMapper);
      }

      return rowMapper.mapRow(rowNum);
   }
}