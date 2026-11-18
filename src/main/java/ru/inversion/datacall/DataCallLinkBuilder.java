package ru.inversion.datacall;

import ru.inversion.dataset.DLBuilder;
import ru.inversion.dataset.IDataLink;
import ru.inversion.dataset.IDataSet;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 */
public class DataCallLinkBuilder {

    private final List< Pair<Function,String> > pairsColumn = new ArrayList<>();
    private Supplier<Boolean> requeryIf = ()->true;
    private Object source, destination;

    /** */
    private DataCallLinkBuilder() {
    }

    /** */
    public DataCallLinkBuilder source( Object source ) {
        this.source = source;return this;
    }

    /** */
    public DataCallLinkBuilder destination( Object destination ) {
        this.destination = destination; return this;
    }

    /** */
    public DataCallLinkBuilder autoRequery( Supplier<Boolean> requeryIf ) {
        this.requeryIf = Objects.requireNonNull(requeryIf, "'requeryIf' is null");
        return this;
    }

    /** */
    public <T> DataCallLinkBuilder join( Function<T,Object> sourceValue, String destinationParam ) {
        if( S.isNullOrEmpty(destinationParam) )
            throw new IllegalArgumentException("'destinationParam' is null or empty");
        pairsColumn.add(Pair.makePair(
                Objects.requireNonNull(sourceValue, "'sourceValue' is null"),
                destinationParam
        ));
        return this;
    }

    /** */
    public static DataCallLinkBuilder NEW( ) {
        return new DataCallLinkBuilder();
    }

    /** */
    public IDataLink build() {

        if( source == null || destination == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "DataCallLinkBuilder: source or destination is null" );

        if( pairsColumn.isEmpty() )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "DataCallLinkBuilder: no join param specified" );

        if( source instanceof IDataSet && destination instanceof IDataCall ) {
            return d2cBuild( );
        }

        throw new UnsupportedOperationException(
                Tags.PRODUCT_LABEL + "DataCallLinkBuilder: unsupported source/destination types: "
                        + source.getClass().getName() + " -> " + destination.getClass().getName()
        );
    }

    /** */
    private <R> IDataLink d2cBuild( ) {
        DLBuilder<IDataSet<R>, IDataCall, R, Object> builder = new DLBuilder<>();
        builder.source((IDataSet<R>)source).destination((IDataCall)destination).requeryIf(requeryIf);
        for (Pair<Function,String> column : pairsColumn)
            builder.joinColumn((Function<R,Object>)column.first, column.second);
        return builder.link();
    }
}
