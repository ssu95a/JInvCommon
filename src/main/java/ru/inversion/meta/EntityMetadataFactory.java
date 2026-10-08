package ru.inversion.meta;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.inversion.meta.impl.EntityMetadataIntrospector;

import java.util.Objects;


/**
 *
 * @author ssu
 */
public class EntityMetadataFactory {

    static final private Cache<Class<?>, IEntityMetaData<?>> metaDataCache;

    static {
        metaDataCache = Caffeine.newBuilder().maximumSize(64).build();
    }

    /** */
    static public <P> IEntityMetaData<P> getEntityMetaData( Class<P> clazz ) {
        
        Objects.requireNonNull( clazz, "clazz is null" );

        final IEntityMetaData<?> emd = metaDataCache.get( clazz, cls -> {
            try {
                //return EntityClassMetadata.loadClassMetaData(cls);
                return EntityMetadataIntrospector.introspect(cls);
            } catch (Exception e) {
                throw new EntityMetaDataException("Failed to load entity metadata for class " + cls.getName(), e);
            }
        });

        return (IEntityMetaData<P>)emd;
    }
}
