package ru.inversion.dataset;

import ru.inversion.datacall.IDataCall;
import ru.inversion.tds.*;
import ru.inversion.utils.Tags;
import ru.inversion.utils.Triplet;
import ru.inversion.utils.U;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** */
public class DLBuilder<S,D,P,T> {

    private static abstract class AbstractLink<S,D,P,T> implements IDataLink<S, D>,
        IDataSetNavigationListener<P>,
        IDataSetRowListener<P>,
        ITreeDataSetNavigationListener<P>,
        ITreeDataSetItemListener<P>,
        ITreeDataSetListener<P>,
        AutoCloseable
    {
        /** */
        protected S source;
        protected D destination;
        protected Supplier<Boolean> requeryIf;
        /** */
        private List<Triplet<Function<P,T>,String,Boolean>> pairsColumn;
        private final Integer filterId;

        /** */
        public AbstractLink( S source, D destination, Supplier<Boolean> requeryIf,
                             List<Triplet<Function<P,T>,String,Boolean>> pairsColumn )
        {
            this.source      = source;
            this.destination = destination;
            this.requeryIf   = requeryIf == null ? ()->true : requeryIf;
            this.pairsColumn = pairsColumn;

            // Добавляем маркер для того, чтобы можно было выделить из общего предиката,
            // тот который устанавливается из родительского dataSet
            final StringBuilder sb = new StringBuilder("/*P[*/(");

            final int l = sb.length();

            if( destination instanceof ISQLDataSet<?> )
            {
                final ISQLDataSet<?> sqds = (ISQLDataSet<?>)destination;

                pairsColumn.forEach((p) -> {
                    sqds.setParameter( p.second, null );
                    if( p.third ) {
                        if( sb.length() > l )
                            sb.append( " and " );
                        sb.append( p.second).append( "= :" ).append( p.second );
                    }
                });
                filterId = sb.length() > l ? sqds.setFilter( sb.append(")/*P]*/").toString(), true, true ) : null;
            }
            else if( destination instanceof SQLTreeDataSet )
            {
                final SQLTreeDataSet<?> sqds = (SQLTreeDataSet<?>)destination;

                pairsColumn.forEach(( p) -> {
                    sqds.setParameter( p.second, null );
                    if( p.third ) {
                        if( sb.length() > l )
                            sb.append( " and " );
                        sb.append( p.second).append( "= :" ).append( p.second );
                    }
                });
                filterId = sb.length() > l ? sqds.setFilter( sb.append(")/*P]*/").toString(), true, true ) : null;
            }
            else
                filterId = null;

            try {
                if( source instanceof IDataSet )
                {
                    final IDataSet<P> ds = (IDataSet<P>)source;

                    ds.addNavigationListener(this);
                    ds.addRowListener(this);

                    P row = ds.getCurrentRow();

                    if( row != null )
                        refreshDestination(row);
                }
                else if( source instanceof ITreeDataSet )
                {
                    final ITreeDataSet<P> ds = (ITreeDataSet<P>)source;

                    ds.addNavigationListener(this);
                    ds.addItemListener(this);
                    ds.addDataSetListener(this);

                    P row = U.callIfNotNull( ds.getCurrentItem(), ITreeDataSetItem::getValue );

                    if( row != null )
                        refreshDestination(row);
                }
            } catch (RuntimeException | Error failure) {
                try {
                    close();
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {

            if( source == null )
                return;

            if( source instanceof IDataSet )
            {
                IDataSet<P> ds = (IDataSet<P>)source;
                ds.removeNavigationListener(this);
                ds.removeRowListener(this);
            }
            else if( source instanceof ITreeDataSet )
            {
                ITreeDataSet<P> ds = (ITreeDataSet<P>)source;
                ds.removeNavigationListener(this);
                ds.removeItemListener      (this);
                ds.removeDataSetListener   (this);
            }

            if( filterId != null )
            {
                if( destination instanceof ISQLDataSet<?> )
                    ((ISQLDataSet<?>)destination).removeFilter(filterId);
                else if( destination instanceof SQLTreeDataSet<?> )
                    ((SQLTreeDataSet<?>)destination).removeFilter(filterId);
            }

            destination = null;
            source 		= null;

            requeryIf   = ()->false;
            pairsColumn = null;
        }

        /** */
        @Override
        public S getSource() {
            return source;
        }

        /** */
        @Override
        public D getDestination() {
            return destination;
        }

        /** */
        @Override
        public void setAutoRequery( Supplier<Boolean> r ) {
            this.requeryIf = r == null ? ()->true : r;
        }

        /** */
        @Override
        public void navigated( DataSetNavigationEvent<P> e ) {
            refreshDestination( e.getNewRow() );
        }

        /** */
        @Override
        public void rowOperation( DataSetRowEvent<P> e ) {
            if( e.getRowOperation() == DataSetRowEvent.RowOperationEnum.REFRESH ) {
                refreshDestination( e.getNewRow() );
            }
        }
        @Override
        public void itemChanged( TreeDataSetItemEvent<P> e ) {
            refreshDestination( e.getNewValue() );
        }

        @Override
        public void navigated( TreeDataSetNavigationEvent<P> e ) {
            refreshDestination( e.getNewItem() == null ? null : e.getNewItem().getValue() );
        }

        @Override
        public void dataSetChanged( TreeDataSetEvent<P> e )
        {
            if( e.isAfter()
                && e.getEventType() == DataSetEvent.DataSetEventType.EXECUTE
                && e.getTreeDataSet().isEmpty()
            )
                refreshDestination( null );
        }

        /** */
        private void check4Closed() {
            if( source == null )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "DLink already closed" );
        }

        /** Обновляет ведомый DataSet */
        private void refreshDestination( P row ) {

            try {

                check4Closed();

                if( destination instanceof ISQLDataSet )
                {
                    final ISQLDataSet<?> sqds = (ISQLDataSet<?>)destination;

                    if( row != null ) {

                        pairsColumn.forEach( (p) -> sqds.setParameter( p.second, p.first.apply(row) ));

                        if( requeryIf.get() )
                            sqds.executeQuery();
                    }
                    else
                    {
                        pairsColumn.stream().forEach( (p) -> sqds.setParameter( p.second, null ));
                        sqds.clear();
                    }
                }
                else if( destination instanceof SQLTreeDataSet )
                {
                    final SQLTreeDataSet<?> sqts = (SQLTreeDataSet<?>)destination;

                    if( row != null )
                    {
                        pairsColumn.forEach( (p) -> sqts.setParameter( p.second, p.first.apply(row) ) );

                        if( requeryIf.get() )
                            sqts.executeQuery();
                    }
                    else
                    {
                        pairsColumn.forEach( (p) -> sqts.setParameter( p.second, null ) );
                        sqts.clear();
                    }
                }
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "DLink: Error on refresh dep dataSet", th );
            }
        }

    }

    /** */
    private static class DataSet2TreeLink<P,T>
            extends AbstractLink<IDataSet<P>,SQLTreeDataSet<?>,P,T>
    {
        public DataSet2TreeLink( IDataSet<P> source, SQLTreeDataSet<?> destination,
                                 Supplier< Boolean > requeryIf, List<Triplet<Function<P,T>,String,Boolean>> pairsColumn ) {
            super( source, destination, requeryIf, pairsColumn );
        }
    }

    /** */
    private static class Tree2DataSetLink<P,T>
            extends AbstractLink<ITreeDataSet<P>,ISQLDataSet<?>,P,T>
    {
        public Tree2DataSetLink( ITreeDataSet<P> source, ISQLDataSet<?> destination,
                                 Supplier< Boolean > requeryIf, List<Triplet<Function<P,T>,String,Boolean>> pairsColumn ) {
            super(source, destination, requeryIf, pairsColumn);
        }
    }

    /** Связь двух DataSet с сохранением контракта IDataSetLink. */
    private static class DataSet2DataSetLink<P,T> implements IDataSetLink<P>,
            IDataSetNavigationListener<P>, IDataSetRowListener<P> {

        private IDataSet<P> source;
        private ISQLDataSet<?> destination;
        private final List<Triplet<Function<P,T>,String,Boolean>> columns;
        private Supplier<Boolean> requeryIf;
        private final int filterId;

        DataSet2DataSetLink(IDataSet<P> source, ISQLDataSet<?> destination,
                            Supplier<Boolean> requeryIf, List<Triplet<Function<P,T>,String,Boolean>> columns) {
            this.source = source;
            this.destination = destination;
            this.requeryIf = requeryIf == null ? () -> true : requeryIf;
            this.columns = columns;

            StringBuilder filter = new StringBuilder("/*P[*/(");
            int prefixLength = filter.length();
            for (Triplet<Function<P,T>,String,Boolean> column : columns) {
                if (column.third) {
                    if (filter.length() > prefixLength)
                        filter.append(" and ");
                    filter.append(column.second).append("=:").append(column.second);
                    destination.setParameter(column.second, null);
                }
            }
            filterId = filter.length() == prefixLength ? 0
                    : destination.setFilter(filter.append(")/*P]*/").toString(), true, true);

            try {
                source.addNavigationListener(this);
                source.addRowListener(this);
                P row = source.getCurrentRow();
                if (row != null)
                    refreshDestination(row);
            } catch (RuntimeException | Error failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @Override public IDataSet<P> getSource() { return source; }
        @Override public ISQLDataSet<?> getDestination() { return destination; }

        @Override public void navigated(DataSetNavigationEvent<P> event) {
            refreshDestination(event.getNewRow());
        }

        @Override public void rowOperation(DataSetRowEvent<P> event) {
            if (event.getRowOperation() == DataSetRowEvent.RowOperationEnum.REFRESH)
                refreshDestination(event.getNewRow());
        }

        private void refreshDestination(P row) {
            try {
                if (source == null)
                    throw new IllegalStateException(Tags.PRODUCT_LABEL + "DataSetLink already closed");
                if (row == null) {
                    destination.clear();
                    for (Triplet<Function<P,T>,String,Boolean> column : columns)
                        destination.setParameter(column.second, null);
                } else {
                    for (Triplet<Function<P,T>,String,Boolean> column : columns)
                        destination.setParameter(column.second, column.first.apply(row));
                    if (requeryIf.get())
                        destination.executeQuery();
                }
            } catch (Throwable th) {
                throw new RuntimeException(Tags.PRODUCT_LABEL + "DataSetLink: Error on refresh dep dataSet", th);
            }
        }

        @Override public void close() {
            if (source == null)
                return;
            source.removeNavigationListener(this);
            source.removeRowListener(this);
            if (filterId != 0)
                destination.removeFilter(filterId);
            source = null;
            destination = null;
            requeryIf = () -> false;
        }

        @Override public boolean isAutoRequery() { return requeryIf.get(); }

        @Override public void setAutoRequery(boolean autoRequery) {
            requeryIf = () -> autoRequery;
        }

        @Override public void setAutoRequery(Supplier<Boolean> value) {
            requeryIf = value == null ? () -> true : value;
        }

        @Override public void initDestinationRow(BiConsumer<Object,String> initializer) {
            if (initializer == null)
                return;
            if (source == null)
                throw new IllegalStateException(Tags.PRODUCT_LABEL + "DataSetLink already closed");
            P row = source.getCurrentRow();
            if (row != null)
                for (Triplet<Function<P,T>,String,Boolean> column : columns)
                    initializer.accept(column.first.apply(row), column.second);
        }
    }

    /** Значения параметров вызова читаются лениво из текущей строки DataSet. */
    private static class DataSet2CallLink<P,T> implements IDataLink<IDataSet<P>,IDataCall>,
            IDataSetNavigationListener<P>, IDataSetRowListener<P>, IDataSetListener, AutoCloseable {

        private IDataSet<P> source;
        private final IDataCall destination;
        private Supplier<Boolean> requeryIf;

        DataSet2CallLink(IDataSet<P> source, IDataCall destination,
                         Supplier<Boolean> requeryIf, List<Triplet<Function<P,T>,String,Boolean>> columns) {
            this.source = source;
            this.destination = destination;
            this.requeryIf = requeryIf == null ? () -> true : requeryIf;
            try {
                for (Triplet<Function<P,T>,String,Boolean> column : columns)
                    destination.setValueCallback(column.second, () -> {
                        P row = source.getCurrentRow();
                        return row == null ? null : column.first.apply(row);
                    });
                source.addNavigationListener(this);
                source.addRowListener(this);
                source.addDataSetListener(this);
            } catch (Throwable th) {
                close();
                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on build link between DataSet and DataCall", th);
            }
        }

        @Override public IDataSet<P> getSource() { return source; }
        @Override public IDataCall getDestination() { return destination; }

        @Override public void navigated(DataSetNavigationEvent<P> event) { refresh(); }

        @Override public void rowOperation(DataSetRowEvent<P> event) {
            if (event.getRowOperation() == DataSetRowEvent.RowOperationEnum.REFRESH)
                refresh();
        }

        @Override public void dataSetChanged(DataSetEvent event) {
            if (event.isBefore() && event.getEventType() == DataSetEvent.DataSetEventType.CLOSE) {
                try {
                    destination.close();
                } finally {
                    close();
                }
            }
        }

        private void refresh() {
            try {
                if (Boolean.TRUE.equals(requeryIf.get()))
                    destination.execute();
            } catch (Throwable th) {
                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on execute dataCall", th);
            }
        }

        @Override public void setAutoRequery(Supplier<Boolean> value) {
            requeryIf = Objects.requireNonNull(value, "'requeryIf' is null");
        }

        @Override public void close() {
            if (source == null)
                return;
            source.removeNavigationListener(this);
            source.removeRowListener(this);
            source.removeDataSetListener(this);
            source = null;
            requeryIf = () -> false;
        }
    }


   /**
    * Данные Builder'a
    */
    private S source;
    private D destination;
    private Supplier<Boolean> requeryIf;
    /** */
    private final List<Triplet<Function<P,T>,String,Boolean>> pairsColumn = new ArrayList<>();
    /** */
    public DLBuilder<S,D,P,T> source( S source ) {
        this.source = source;
        return this;
    }
    /** */
    public DLBuilder<S,D,P,T> destination( D destination ) {
        this.destination = destination;
        return this;
    }
    /** */
    public DLBuilder<S,D,P,T> requeryIf( Supplier<Boolean> r ) {
        this.requeryIf = r;
        return this;
    }

    /** */
    public DLBuilder<S,D,P,T> joinColumn ( Function<P,T> sourceValue, String destinationColumn ) {
        return joinColumn(sourceValue, destinationColumn, true);
    }

    /** Добавляет параметр связи; при false значение не входит в SQL-фильтр. */
    public DLBuilder<S,D,P,T> joinColumn( Function<P,T> sourceValue, String destinationColumn, boolean useInFilter ) {
        pairsColumn.add( Triplet.makeTriplet(sourceValue, destinationColumn, useInFilter) );
        return this;
    }

    /**
     * Создает связь между объектами работы с данными.
     * @return
     *         {@link IDataLink}
     * @throws
     *         IllegalStateException если не все параметры корректно установлены.
     */
    public IDataLink<S,D> link( ) {

        if( source == null || destination == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "DataLinkBuilder: source or destination is null" );

        if( pairsColumn.isEmpty() )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "DataLinkBuilder: no join column specified" );

        if(  source instanceof IDataSet && destination instanceof ISQLDataSet )
        {
            return (IDataLink<S,D>)new DataSet2DataSetLink<>(
                    (IDataSet<P>)source, (ISQLDataSet<?>)destination, requeryIf, new ArrayList<>(pairsColumn));
        }

        if( source instanceof IDataSet && destination instanceof SQLTreeDataSet )
        {
            final DataSet2TreeLink<P,T> dlb = new DataSet2TreeLink<P,T>( (IDataSet<P>)source, (SQLTreeDataSet<?>) destination, this.requeryIf, new ArrayList<>(pairsColumn) );
            return (IDataLink< S, D >)dlb;
        }

        if( source instanceof ITreeDataSet && destination instanceof ISQLDataSet )
        {
            final Tree2DataSetLink<P,T> dlb = new Tree2DataSetLink<P,T>( (ITreeDataSet<P>)source, (ISQLDataSet<?>) destination, this.requeryIf, new ArrayList<>(pairsColumn) );
            return (IDataLink< S, D >)dlb;
        }

        if( source instanceof IDataSet && destination instanceof IDataCall )
        {
            for (Triplet<Function<P,T>,String,Boolean> column : pairsColumn) {
                if (ru.inversion.utils.S.isNullOrEmpty(column.second))
                    throw new IllegalArgumentException("'destinationParam' is null or empty");
                Objects.requireNonNull(column.first, "'sourceValue' is null");
            }
            return (IDataLink<S,D>)new DataSet2CallLink<>(
                    (IDataSet<P>)source, (IDataCall)destination, requeryIf, new ArrayList<>(pairsColumn));
        }

        return null;
    }

    /** */
    public static <S,D,P,T> IDataLink<S,D> link( S source, D destination, Function<P,T> sourceValue, String destinationColumn, Supplier<Boolean> r )
    {
        DLBuilder<S,D,P,T> dlb = new DLBuilder<>();
        return  dlb.source(source).destination(destination).joinColumn( sourceValue, destinationColumn ).requeryIf(r).link();
    }

}
