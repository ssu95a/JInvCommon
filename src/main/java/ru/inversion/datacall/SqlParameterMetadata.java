package ru.inversion.datacall;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.utils.S;

import javax.persistence.ParameterMode;
import java.util.Objects;

class SqlParameterMetadata implements ParameterMetadata<Object>{

    final private ParameterMetadata<?> base;
    final private String elementTypeResolved;
    /** */
    public SqlParameterMetadata( ParameterMetadata<?> base, SqlDialect dialect ) {
        this.base = Objects.requireNonNull( base, "'base' is null");
        final String r = base.getElementType();
        elementTypeResolved = S.isNullOrEmpty(r) ? null : dialect.elementType(r);
    }

    @Override
    public Integer getSQLType() {
        return base.getSQLType();
    }

    @Override
    public ParameterMode getMode() {
        return base.getMode();
    }

    @Override
    public String getName() {
        return base.getName();
    }

    @Override
    public Integer getPosition() {
        return base.getPosition();
    }

    @Override
    public Class<Object> getParameterType() {
        return (Class<Object>)base.getParameterType();
    }

    @Override
    public String getElementType() {
        return elementTypeResolved;
    }

    @Override
    public Object getDefaultValue() {
        return base.getDefaultValue();
    }

    @Override
    public boolean isLogValue() {
        return base.isLogValue();
    }

    @Override
    public boolean isReturnValue() {
        return base.isReturnValue();
    }

    /** */
    public static boolean hasDialectSpecific( ParameterMetadata<?> pm )
    {
        if( pm == null )
            return false;

        return !S.isNullOrEmpty( pm.getElementType() );
    }
}
