package ru.inversion.meta.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.meta.EntityMetaDataException;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TCStorage;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.converter.TypeConverter;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Metadata holder for SQL STRUCT
 */
public class StructMetaData {

    private static final Cache<String, StructMetaData> metaDataCache;

    static {

        metaDataCache = Caffeine.newBuilder()
                .maximumSize(32)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                /*.recordStats()*/
                .build();
    }

    /**
     * Пытаемся использовать common TaskContext, если он есть.
     */
    private static TaskContext resolveTaskContext( TaskContext taskContext )
    {
        TaskContext tc = U.nvl(
            TCStorage.INSTANCE().getBy(tc1 -> Boolean.TRUE.equals(tc1.getProperty("common"))),
            taskContext
        );

        if( tc == null )
            throw new EntityMetaDataException ( Tags.PRODUCT_LABEL + "TaskContext is required for STRUCT metadata operation." );

        return tc;
    }

    private static String makeCacheKey(TaskContext taskContext, String sqlTypeName) throws Exception {

        Objects.requireNonNull(taskContext, "'taskContext' is null");

        Connection connection    = taskContext.getConnection();

        final String url         = connection.getMetaData().getURL();
        final String dialectName = taskContext.dialect().type().name();

        return dialectName
                + ":"
                + U.nvl(url, "<no-url>")
                + ":"
                + S.nvl(sqlTypeName);
    }

    /**
     * Loads STRUCT metadata through dialect
     */
    public static StructMetaData get( TaskContext taskContext, String sqlTypeName )
    {
        try {

            Checks.Require.text( sqlTypeName, "sqlTypeName");

            final TaskContext tc  = resolveTaskContext(taskContext);
            final String cacheKey = makeCacheKey( tc, sqlTypeName);

            return metaDataCache.get(cacheKey, new Function<String, StructMetaData>() {
                @Override
                public StructMetaData apply(String key) {
                    try {

                        ResultSetMetaData rsmd = tc.dialect().getStructMetaData(tc, sqlTypeName);

                        return new StructMetaData(sqlTypeName, rsmd);
                    }
                    catch (Throwable th) {
                        throw new RuntimeException( Tags.PRODUCT_LABEL + "Error load new instance meta info of '" + sqlTypeName + "'", th );
                    }
                }
            });
        }
        catch (Throwable th) {
            throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "Error loading meta information about the '" + S.nvl(sqlTypeName) + "' struct", th );
        }
    }

    private final String sqlTypeName;
    private final ResultSetMetaData metaData;

    private StructMetaData(String sqlTypeName, ResultSetMetaData metaData) {
        this.sqlTypeName = Objects.requireNonNull( sqlTypeName, "'sqlTypeName' is null");
        this.metaData    = Objects.requireNonNull( metaData, "'metaData' is null");
    }

    public String getSQLTypeName() {
        return sqlTypeName;
    }

    public ResultSetMetaData asResultSetMetaData() {
        return metaData;
    }

    /**
     * Creates mapper from STRUCT attributes to entity POJO.
     *
     * Важно: список сохраняет позиционность STRUCT.
     * Даже если property == null, entry остаётся в list.
     */
    public <P> IStructMapper<P> createMapper( IEntityMetaData<P> emd ) throws Exception {

        Objects.requireNonNull( emd, "'emd' is null" );

        final List<StructAttributeBinding<P>> bindings = new ArrayList<>();

        ResultSetMetaData rs = this.asResultSetMetaData();

        int matchedCount = 0;

        for( int i = 1; i <= rs.getColumnCount(); i++ )
        {
            final String attributeName  = rs.getColumnName(i);
            final int columnType        = rs.getColumnType(i);
            final String columnTypeName = rs.getColumnTypeName(i);

            final IEntityProperty<P, ?> property = emd.getProperty(attributeName);

            if( property != null )
                matchedCount++;

            /*
             * Важно:
             * binding добавляется всегда, даже если property == null.
             * Так сохраняется позиционность STRUCT attributes.
             */
            bindings.add( new StructAttributeBinding<>( i, attributeName, property, columnType, columnTypeName ) );
        }

        if( matchedCount == 0 )
        {
            throw new IllegalStateException (
                Tags.PRODUCT_LABEL + "Не возможно сделать мапинг между STRUCT '" + this.sqlTypeName + "' и java POJO '" + emd.getEntityClass().getName() + "'. Нет совпадающих по имени свойств!\n"+ "Проверьте регистр имени свойств!"
            );
        }

        return new EntityStructMapper<>(getSQLTypeName(), emd, bindings);
    }

    /** */
    public <P> IStructMapper<P> getMapper(Class<P> classFor) {

        try {

            Objects.requireNonNull(classFor, "'classFor' is null");

            IStructMapper<P> mapper = (IStructMapper<P>) TypeConverter.getStructConverter(getSQLTypeName(), classFor);

            if( mapper == null )
            {
                mapper = createMapper(EntityMetadataFactory.getEntityMetaData(classFor));
                TypeConverter.registerStructConverter(getSQLTypeName(), classFor, mapper);
            }

            return mapper;

        }
        catch (Throwable th) {
            throw new EntityMetaDataException(
                    Tags.PRODUCT_LABEL
                            + "Error on getMapper from struct '"
                            + getSQLTypeName()
                            + "' to class "
                            + (classFor == null ? null : classFor.getName()),
                    th
            );
        }
    }

    public JsonObject toJson(Struct struct) {

        try {
            Objects.requireNonNull(struct, "'struct' is null");

            final JsonObjectBuilder job = Json.createObjectBuilder();

            final Object[] attributes = struct.getAttributes();

            if (attributes == null) {
                throw new IllegalStateException(
                        Tags.PRODUCT_LABEL
                                + "Struct attributes is null for '"
                                + getSQLTypeName()
                                + "'"
                );
            }

            final int nSize = Math.min(metaData.getColumnCount(), attributes.length);

            for (int i = 0; i < nSize; i++) {
                Object value = attributes[i];
                String attr = metaData.getColumnName(i + 1);

                if (value == null) {
                    job.addNull(attr);
                } else {
                    job.add(attr, TypeConverter.convert(value, String.class));
                }
            }

            // Если attributes короче metadata, недостающие поля явно устанавливаем в null.
            for( int i = nSize; i < metaData.getColumnCount(); i++ )
            {
                job.addNull( metaData.getColumnName(i + 1) );
            }

            return job.build();
        }
        catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make json from struct '"
                            + getSQLTypeName()
                            + "' object",
                    th
            );
        }
    }

    public JsonArrayBuilder toJson(Object[] array) {

        try {
            Objects.requireNonNull(array, "'array' is null");

            final JsonArrayBuilder jab = Json.createArrayBuilder();
            final int nSize = metaData.getColumnCount();

            for (Object item : array) {

                if (item == null) {
                    jab.addNull();
                    continue;
                }

                Struct struct = (Struct) item;
                final Object[] attributes = struct.getAttributes();

                if (attributes == null) {
                    throw new IllegalStateException(
                            Tags.PRODUCT_LABEL
                                    + "Struct attributes is null for '"
                                    + getSQLTypeName()
                                    + "'"
                    );
                }

                final JsonObjectBuilder job = Json.createObjectBuilder();

                final int attrSize = Math.min(nSize, attributes.length);

                for (int j = 0; j < attrSize; j++) {
                    Object value = attributes[j];
                    String attr = metaData.getColumnName(j + 1);

                    if (value == null) {
                        job.addNull(attr);
                    } else {
                        job.add(attr, TypeConverter.convert(value, String.class));
                    }
                }

                for (int j = attrSize; j < nSize; j++) {
                    job.addNull(metaData.getColumnName(j + 1));
                }

                jab.add(job.build());
            }

            return jab;
        }
        catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make json from struct '"
                            + getSQLTypeName()
                            + "' object",
                    th
            );
        }
    }

    private Struct fromJsonObject(TaskContext tc, JsonObject job) throws Exception {

        Objects.requireNonNull(tc, "'tc' is null");
        Objects.requireNonNull(job, "'job' is null");

        int nSize = metaData.getColumnCount();
        Object[] attributes = new Object[nSize];

        for (int i = 0; i < nSize; i++) {

            final String attr = metaData.getColumnName(i + 1);
            final JsonValue jv = job.get(attr);

            if (jv == null || jv == JsonValue.NULL) {
                attributes[i] = null;
                continue;
            }

            switch (jv.getValueType()) {
                case NULL:
                    attributes[i] = null;
                    break;

                case TRUE:
                case FALSE:
                    attributes[i] = job.getBoolean(attr) ? 1 : 0;
                    break;

                case STRING:
                    attributes[i] = job.getString(attr);
                    break;

                case NUMBER: {
                    final JsonNumber jnum = job.getJsonNumber(attr);
                    attributes[i] = jnum.isIntegral()
                            ? jnum.longValue()
                            : jnum.bigDecimalValue();
                    break;
                }

                default:
                    throw new IllegalArgumentException(
                            Tags.PRODUCT_LABEL
                                    + "Unsupported json value type '"
                                    + jv.getValueType()
                                    + "' for STRUCT attribute '"
                                    + attr
                                    + "' of '"
                                    + getSQLTypeName()
                                    + "'"
                    );
            }
        }

        return tc.dialect().createStruct(tc, sqlTypeName, attributes);
    }

    public Struct fromJson(TaskContext tc, String jsonStr) {

        try {
            Objects.requireNonNull(jsonStr, "'jsonStr' is null");

            TaskContext resolvedTc = resolveTaskContext(tc);

            try (JsonReader jsr = Json.createReader(new StringReader(jsonStr))) {
                return fromJsonObject(resolvedTc, jsr.readObject());
            }
        }
        catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make struct from json '"
                            + getSQLTypeName()
                            + "' object",
                    th
            );
        }
    }

    /** */
    public Struct fromJson(String jsonStr) {
        return fromJson(null, jsonStr);
    }

    public List<Struct> fromJsonArray(TaskContext tc, String jsonStr) {

        try {
            Objects.requireNonNull(jsonStr, "'jsonStr' is null");

            TaskContext resolvedTc = resolveTaskContext(tc);

            final JsonArray jsa;

            try (JsonReader jsr = Json.createReader(new StringReader(jsonStr))) {
                jsa = jsr.readArray();
            }

            final List<Struct> retList = new ArrayList<>(jsa.size());

            for (JsonValue jov : jsa) {

                if (jov == JsonValue.NULL) {
                    retList.add(null);
                    continue;
                }

                if (jov.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalArgumentException(
                            Tags.PRODUCT_LABEL
                                    + "Expected JSON object in struct array '"
                                    + getSQLTypeName()
                                    + "', but got "
                                    + jov.getValueType()
                    );
                }

                retList.add(fromJsonObject(resolvedTc, (JsonObject) jov));
            }

            return retList;
        }
        catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make struct array from json '"
                            + getSQLTypeName()
                            + "' object",
                    th
            );
        }
    }

    /** */
    public List<Struct> fromJsonArray(String jsonStr) {
        return fromJsonArray(null, jsonStr);
    }

    /** */
    public Struct fromMap(TaskContext tc, Map<String, Object> m) {

        try {

            Objects.requireNonNull( m, "'map' is null" );

            TaskContext resolvedTc = resolveTaskContext(tc);

            final int nSize = metaData.getColumnCount();
            final Object[] attributes = new Object[nSize];

            for( int i = 0; i < nSize; i++ )
            {
                 final String attr = metaData.getColumnName(i + 1);
                 attributes[i] = m.get(attr);
            }

            return resolvedTc.dialect().createStruct( resolvedTc, sqlTypeName, attributes );
        }
        catch( Throwable th ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on make struct from map '" + getSQLTypeName() + "' object", th );
        }
    }

    /** */
    public Struct fromMap(Map<String, Object> m) {
        return fromMap(null, m);
    }

    public List<Struct> fromMapsArray(TaskContext tc, Collection<Map<String, Object>> ma) {

        try {
            Objects.requireNonNull(ma, "'maps array' is null");

            TaskContext resolvedTc = resolveTaskContext(tc);

            return ma.stream()
                    .map(m -> fromMap(resolvedTc, m))
                    .collect(Collectors.toList());
        }
        catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make struct from map array",
                    th
            );
        }
    }

    /** */
    public List<Struct> fromMapsArray(Collection<Map<String, Object>> ma) {
        return fromMapsArray(null, ma);
    }

    public <T> T map(Struct struct, Class<T> classTo) {
        return getMapper(classTo).from(struct);
    }

    /** */
    public <T> Struct map( TaskContext tc, T value )
    {

        try {

            if( value == null )
                return null;

            TaskContext resolvedTc = resolveTaskContext(tc);

            return this.getMapper((Class<T>) value.getClass()).toStruct(resolvedTc, value);
        }
        catch (Throwable th) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL
                            + "Error on make struct '"
                            + getSQLTypeName()
                            + "' from value",
                    th
            );
        }
    }

    /** */
    public <T> Struct map(T value) {
        return map(null, value);
    }
}