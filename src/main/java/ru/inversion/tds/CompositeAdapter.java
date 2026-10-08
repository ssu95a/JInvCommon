package ru.inversion.tds;

import ru.inversion.db.entity.ParentId;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.utils.Tags;

/** */
public class CompositeAdapter<P, D extends Comparable<D>> {

    private final IEntityProperty<P, D> idProperty;
    private final IEntityProperty<P, D> parentIdProperty;

    public CompositeAdapter(Class<P> clazz) {
        this(EntityMetadataFactory.getEntityMetaData(clazz));
    }

    public CompositeAdapter(IEntityMetaData<P> meta) {

        if( meta.getIDList().isEmpty() )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "TreeDataSet requires exactly one ID property for class " + meta.getEntityClass().getName() );

        if( meta.getIDList().size() > 1 )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Для ID из нескольких полей, работа с древовидными структурами не поддерживается" );

        this.idProperty = (IEntityProperty<P, D>) meta.getIDList().get(0);

        IEntityProperty<P, D> parentProperty = null;

        for (IEntityProperty<P, ?> property : meta.getPropertiesMap().values()) {
            if (property.getAnnotation(ParentId.class) != null) {
                parentProperty = (IEntityProperty<P, D>) property;
                break;
            }
        }

        if( parentProperty == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "В классе '" + meta.getEntityClass().getName() + "' отсутствует поле с аннотацией @ParentID, работа с древовидными структурами не возможна." );

        this.parentIdProperty = parentProperty;
    }

    /** */
    public IEntityProperty<P, D> getIdProperty() {
        return idProperty;
    }

    /** */
    public IEntityProperty<P, D> getParentIdProperty() {
        return parentIdProperty;
    }

    /** */
    public D getId(P pojo) {
        return pojo == null ? null : idProperty.invokeGetter(pojo);
    }

    /** */
    public D getParentId(P pojo) {
        return pojo == null ? null : parentIdProperty.invokeGetter(pojo);
    }
}