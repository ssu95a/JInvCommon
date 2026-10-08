package ru.inversion.meta.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.inversion.datacall.CallException;
import ru.inversion.meta.EntityMetaDataException;
import ru.inversion.tc.TCStorage;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import javax.json.*;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.*;
import java.util.*;

/** */
public class ArrayMetaData {

    private static final Cache<String, ArrayMetaData> metaDataCache;

    static {
        metaDataCache = Caffeine.newBuilder().maximumSize(32).build();
    }

    /** */
    private static TaskContext resolveTaskContext(TaskContext taskContext) {

        TaskContext tc = U.nvl (
            TCStorage.INSTANCE().getBy(tc1 -> Boolean.TRUE.equals(tc1.getProperty("common"))),
            taskContext
        );

        if( tc == null )
            throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "TaskContext is required for ARRAY metadata operation.",
                                               new IllegalStateException( Tags.PRODUCT_LABEL + "TaskContext is null" ) );
        return tc;
    }

    /** */
    private static String makeCacheKey( TaskContext taskContext, String sqlTypeName ) throws Exception {

        Objects.requireNonNull( taskContext, "'taskContext' is null" );

        String url = taskContext.getConnection().getMetaData().getURL();

        return taskContext.dialect().type()
                + ":"
                + U.nvl(url, "<no-url>")
                + ":"
                + S.nvl(sqlTypeName);
    }

    /** */
    public static ArrayMetaData get(TaskContext taskContext, String sqlTypeName) {

        try {

            Checks.Require.text( sqlTypeName, "sqlTypeName" );

            final TaskContext tc = resolveTaskContext(taskContext);
            final String cacheKey = makeCacheKey(tc, sqlTypeName);

            return metaDataCache.get(cacheKey, key -> tc.dialect().getArrayMetaData(tc, sqlTypeName));

        } catch( Throwable th ) {
            throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "Error loading meta information about the " + S.nvl(sqlTypeName) + " array", th );
        }
    }

    /** */
    public static Array createArray(TaskContext tc, String sqlTypeName, Collection<?> col) {
        return get(tc, sqlTypeName).createArray(tc, col);
    }

    private final String sqlTypeName;
    private final String baseTypeName;
    private final int baseType;

    /** */
    public ArrayMetaData(String sqlTypeName, String baseTypeName, int baseType) {
        this.sqlTypeName  = Checks.Require.text( sqlTypeName, "sqlTypeName" );
        this.baseTypeName = Checks.Require.text( baseTypeName, "baseTypeName" );
        this.baseType     = baseType;
    }

    /** */
    public String getSqlTypeName() {
        return sqlTypeName;
    }

    /** */
    public String getBaseTypeName() {
        return baseTypeName;
    }

    /** */
    public int getBaseType() {
        return baseType;
    }

    /** */
    public Array createArray( TaskContext tc, Collection<?> col )
    {
        try {

            TaskContext resolvedTc = resolveTaskContext(tc);

            Collection<?> safeCol = col == null
                    ? Collections.emptyList()
                    : col;

            return resolvedTc.dialect().createArray( resolvedTc, this, safeCol );

        } catch (Throwable th) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error creating instance of type ARRAY '" + getSqlTypeName() + "'", th );
        }
    }

    /** */
    public String toJson(TaskContext tc, Array array) {

        try {
            Objects.requireNonNull(array, "'array' is null");

            final JsonArrayBuilder jab;

            if (getBaseType() == Types.STRUCT) {
                jab = StructMetaData
                        .get(tc, getBaseTypeName())
                        .toJson((Object[]) array.getArray());
            } else {
                final Object[] v = (Object[]) array.getArray();

                jab = Json.createArrayBuilder();

                for (Object item : v) {
                    if (item == null) {
                        jab.addNull();
                    } else {
                        jab.add(TypeConverter.convert(item, String.class));
                    }
                }
            }

            final StringWriter sw = new StringWriter();
            Json.createWriter(sw).writeArray(jab.build());

            return sw.toString();

        } catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make json from array '"
                            + getSqlTypeName()
                            + "' with base type '"
                            + getBaseTypeName()
                            + "'",
                    th
            );
        }
    }

    /** */
    public String toJson(Array array) {
        return toJson(null, array);
    }

    /** */
    public static Object getJsonValue(JsonValue jv) {

        if (jv == null || jv == JsonValue.NULL) {
            return null;
        }

        switch (jv.getValueType()) {
            case NULL:
                return null;

            case TRUE:
                return Boolean.TRUE;

            case FALSE:
                return Boolean.FALSE;

            case STRING:
                return ((JsonString) jv).getString();

            case NUMBER: {
                final JsonNumber jnum = (JsonNumber) jv;
                return jnum.isIntegral()
                        ? jnum.longValue()
                        : jnum.bigDecimalValue();
            }

            case OBJECT:
            case ARRAY:
                return jv;

            default:
                return null;
        }
    }

    /** */
    public List<Object> fromJson(String jsonStr) {

        try {
            Objects.requireNonNull(jsonStr, "'jsonStr' is null");

            if (getBaseType() == Types.STRUCT) {
                throw new IllegalStateException(
                        Tags.PRODUCT_LABEL
                                + "Only simple type support for this method. STRUCT not supported."
                );
            }

            final JsonArray jsa;

            try (JsonReader reader = Json.createReader(new StringReader(jsonStr))) {
                jsa = reader.readArray();
            }

            final List<Object> retList = new ArrayList<>(jsa.size());

            for (JsonValue jov : jsa) {
                retList.add(getJsonValue(jov));
            }

            return retList;

        } catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make array from json string for '"
                            + getBaseTypeName()
                            + "' array type",
                    th
            );
        }
    }

    /** */
    public <T> Array makeArray(Collection<T> items) {

        Collection<T> safeItems = items == null
                ? Collections.emptyList()
                : items;

        return new ArrayImpl(this, safeItems.toArray());
    }

    private static class ArrayImpl implements Array {

        private final ArrayMetaData metaData;

        private Object data;

        private ArrayImpl( ArrayMetaData metaData, Object data )
        {
            this.metaData = Checks.Require.object( metaData, "metaData");
            this.data     = data;
        }

        @Override
        public String getBaseTypeName() throws SQLException {
            return metaData.getBaseTypeName();
        }

        @Override
        public int getBaseType() throws SQLException {
            return metaData.getBaseType();
        }

        @Override
        public Object getArray() throws SQLException {
            return data;
        }

        @Override
        public Object getArray(Map<String, Class<?>> map) throws SQLException {
            throw new SQLFeatureNotSupportedException("Not supported yet.");
        }

        @Override
        public Object getArray(long index, int count) throws SQLException {
            throw new SQLFeatureNotSupportedException("Not supported yet.");
        }

        @Override
        public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
            throw new SQLFeatureNotSupportedException("Not supported yet.");
        }

        @Override
        public ResultSet getResultSet() throws SQLException {
            throw new SQLFeatureNotSupportedException("Not supported yet.");
        }

        @Override
        public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
            throw new SQLFeatureNotSupportedException("Not supported yet.");
        }

        @Override
        public ResultSet getResultSet(long index, int count) throws SQLException {
            throw new SQLFeatureNotSupportedException("Not supported yet.");
        }

        @Override
        public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
            throw new SQLFeatureNotSupportedException("Not supported yet.");
        }

        @Override
        public void free() throws SQLException {
            data = null;
        }
    }
}