package ru.inversion.meta.impl.work;

import ru.inversion.dataset.SQLDataSet;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.meta.IEntityColumn;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static ru.inversion.dataset.IDataSet.DataQueryModeEnum.SINGLE_ROW;

/** */
public abstract class WorkBaseLoad<P,V,R> extends WorkBase<P,V,R> {

    /** */
    final static public BiPredicate< IEntityColumn, String > columnFilter = new BiPredicate<IEntityColumn, String>() {
        @Override
        public boolean test( IEntityColumn column, String tableName) {
            return column.getQueryExpr() == null && ( S.isNullOrEmpty( column.getTableName() ) || tableName.equalsIgnoreCase( column.getTableName() ) );
            //return S.isNullOrEmpty(column.getQueryExpr()) && (S.isNullOrEmpty(column.getTableName()) || tableName.equalsIgnoreCase(column.getTableName()));
        }
    };

    /** */
    final static public BiPredicate< IEntityColumn, String > columnFilterFull = ( column, tableName ) ->
    ( S.isNullOrEmpty( column.getTableName() ) || tableName.equalsIgnoreCase( column.getTableName() ) );

    final protected SQLDataSet<P> dsLoad;

    public WorkBaseLoad( IEntityMetaData<P> entityMetaData, TaskContext tc, Object param )
    {
        super( entityMetaData, tc );

        dsLoad = new SQLDataSet<>( tc, entityMetaData.getEntityClass( ) );
        dsLoad.setSQL( prepareSql( param) );
        dsLoad.setDataQueryModeEnum( SINGLE_ROW );
    }

    /** */
    protected String prepareSql( Object param )
    {
        final SqlDialect dialect = tc.dialect();

        final Class<P> clazz = entityMetaData.getEntityClass( );

        final List< IEntityProperty<P,?> > idList = requireIdList();

        final StringBuilder sbSQL = new StringBuilder();

        final String nativeSql = XXIDsDao.getNativeQuery( tc, clazz, null );

        // Загружаем либо для анонимного SQL либо строим по таблице
        // Анонимный имеет приоритет
        if( nativeSql == null )
        {
            final String tableName = entityMetaData.getTableName();

            //fullSetAttrs признак, что необходимо загрузить все столбцы, в том числе и вычисляемые
            Boolean fullSetAttrs = (Boolean)param;
            final BiPredicate< IEntityColumn, String > filter = fullSetAttrs ? columnFilterFull : columnFilter;

            Map<String, IEntityProperty<P,?>> columnsMap = entityMetaData.getColumnsMap( );

            sbSQL.append( columnsMap.
                values( ).
                stream( ).
                filter( (m)->filter.test ( m.getColumnInfo(), tableName ) ).
                   map( dialect::columnName ).
                collect (
                    Collectors.joining( ",", "SELECT ", " FROM " + dialect.tableName(tableName) )
                )
            );
        }//end if
        else
        {
            sbSQL.append("select * from (").append(nativeSql).append(") qrslt ");
        }

        int i = 0;

        for( IEntityProperty<P,?> pk : idList )
        {
            final String pkCol = dialect.columnName(pk);
            final String pkPrm = dialect.pkName(pk);

            sbSQL.append( i++==0 ? " WHERE " : " AND ").append(pkCol).append(" = :").append(pkPrm);
        }

        return sbSQL.toString();
    }

    @Override
    protected String workName() {
        return dsLoad.getName();
    }
}
