package ru.inversion.utils;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

/** */
public interface IJsonMapper<P> {
    /** */
    default String toJson( P p)
    {
        final StringWriter w = new StringWriter(); try { this.toJson(p,w); } catch (final IOException e) { throw new UncheckedIOException(e);} return w.toString();
    }
    /** */
    default void toJson( P p, Writer w) throws IOException { throw new UnsupportedOperationException("toJson(Writer)");}
    /** */
    default <J> J toJsonO( P p ){ throw new UnsupportedOperationException("toJsonO(P)");}
    /** */
    default <B> void toJsonB( P p, B b ){ throw new UnsupportedOperationException("toJsonB(P,B)");}
    /** */
    default String toJsonArray(Iterable<P> pl)
    {
        final StringWriter w = new StringWriter(); try { this.toJsonArray(pl,w); } catch (final IOException e) {throw new UncheckedIOException(e);} return w.toString();
    }
    /** */
    default void toJsonArray( Iterable<P> pl, Writer w) throws IOException { throw new UnsupportedOperationException("toJsonArray(Writer)");}
    /** */
    default <A> A toJsonArrayO( Iterable<P> pl){ throw new UnsupportedOperationException("toJsonArrayO(pl)");}
    /** */
    default <C> void toJsonArrayB( Iterable<P> pl, C b ){ throw new UnsupportedOperationException("toJsonArrayB(pl,B)");}
    /** */
    default P fromJson( String s ){ throw new UnsupportedOperationException("fromJson(s)");}
    /** */
    default List<P> fromJsonArray( String s ){ throw new UnsupportedOperationException("fromJsonArray(s)");}
}

