package ru.inversion.dataset;

import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.IConverter;

import java.sql.SQLException;
import java.sql.Struct;

/** */
public interface IStructMapper<P> extends IConverter<P,Struct> {

    public Struct toStruct( TaskContext tc, P value ) throws SQLException;

    @Override
    default Struct to( P value ) {
        try {
            return toStruct(null,value);
        } catch(SQLException e) {
            throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on cast " + value.getClass().getName() + " to STRUCT", e);
        }
    }

    public String getSQLTypeName();

}
