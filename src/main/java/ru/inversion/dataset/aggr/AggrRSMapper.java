package ru.inversion.dataset.aggr;

import ru.inversion.dataset.IRowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author ssu
 */
public class AggrRSMapper implements IRowMapper<Map<String,Object>> {
   
    final static public AggrRSMapper instance = new AggrRSMapper();
    
    /** */
    @Override
    public Map<String, Object> mapRow( ResultSet rs, int rowNum ) throws SQLException {
        
        Map<String, Object> retMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        
        ResultSetMetaData metaData = rs.getMetaData();
        
        for( int i = metaData.getColumnCount(); i > 0; i-- ) {
             
            String column = metaData.getColumnName(i);
            Object value  = rs.getObject(i);
             
             retMap.put( column, value );
        }
        
        return retMap;
    }

}
