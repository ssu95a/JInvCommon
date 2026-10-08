package ru.inversion.meta;

import ru.inversion.dataset.IRSRowMapper;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.IJsonMapper;

import java.io.Serializable;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author ssu
 */
public interface IEntityMetaData<P> {

    /**
     *
     */
    Class<P> getEntityClass();

    /**
     *
     */
    IEntityProperty<P, ?> getProperty(String property);

    /**
     *
     */
    List<IEntityProperty<P, ?>> getIDList();

    /**
     *
     */
    Map<String, IEntityProperty<P, ?>> getColumnsMap();

    /**
     *
     */
    Map<String, IEntityProperty<P, ?>> getPropertiesMap();

    /**
     *
     */
    IRowMapper<P> getRowMapper();

    /**
     *
     */
    IRSRowMapper<P> createRSRowMapper(ResultSet rs);

    /**
     *
     */
    IEntityDao<P, Serializable> getEntityDao(TaskContext tc);

    /**
     *
     */
    String getTableName();

    /**
     *
     */
    String getName();

    /**
     *
     */
    IStructMapper<P> getStructMapper(TaskContext tc, String structTypeName);

    /**
     *
     */
    IJsonMapper<P> getJSonMapper();

    /**
     *
     */
    Comparator<P> getIdComparator();

    /** */
    P newInstance();

}
