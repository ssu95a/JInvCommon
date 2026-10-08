package ru.inversion.utils;

import java.io.IOException;
import java.io.Writer;

/** */
public interface IJsonable {
    /** */
    default String toJson() {throw new UnsupportedOperationException("toJson");}
    /** */
    default void toJson( Writer w)  { throw new UnsupportedOperationException("toJson(Writer)");}
    /** */
    default <J> J toJsonO( ){ throw new UnsupportedOperationException("toJson(O)");}
    /** */
    default <B> void toJsonB( B b ){ throw new UnsupportedOperationException("toJson(B)");}
}
