package ru.inversion.dataset;

import ru.inversion.datacall.DataCallEvent;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.IDataCallListener;
import ru.inversion.tds.*;
import ru.inversion.utils.Tags;
import ru.inversion.utils.Triplet;
import ru.inversion.utils.U;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** */
public class DLBuilder<S,D,P,T> {

    // При закрытии выполняем каждую операцию и сохраняем первую ошибку.
    private static Throwable cleanup(Throwable failure, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error error) {
            if (failure == null)
                return error;
            if (failure != error)
                failure.addSuppressed(error);
        }
        return failure;
    }

    private static void throwCleanupFailure(Throwable failure) {
        if (failure instanceof Error)
            throw (Error)failure;
        if (failure != null)
            throw (RuntimeException)failure;
    }

    private static abstract class AbstractLink<S,D,P,T> implements IDataLink<S, D>,
        IDataSetNavigationListener<P>,
        IDataSetRowListener<P>,
        ITreeDataSetNavigationListener<P>,
        ITreeDataSetItemListener<P>,
        ITreeDataSetListener<P>,
        IDataCallListener,
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
                else if( source instanceof IDataCall )
                    ((IDataCall)source).addDataCallListener(this);
            } catch (RuntimeException | Error failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure)
                        failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @Override
        public void close() {

            if( source == null )
                return;

            Throwable failure = null;
            try {
                if( source instanceof IDataSet ) {
                    IDataSet<P> ds = (IDataSet<P>)source;
                    failure = cleanup(failure, () -> ds.removeNavigationListener(this));
                    failure = cleanup(failure, () -> ds.removeRowListener(this));
                }
                else if( source instanceof ITreeDataSet ) {
                    ITreeDataSet<P> ds = (ITreeDataSet<P>)source;
                    failure = cleanup(failure, () -> ds.removeNavigationListener(this));
                    failure = cleanup(failure, () -> ds.removeItemListener(this));
                    failure = cleanup(failure, () -> ds.removeDataSetListener(this));
                }
                else if( source instanceof IDataCall ) {
                    IDataCall call = (IDataCall)source;
                    failure = cleanup(failure, () -> call.removeDataCallListener(this));
                }

                if( filterId != null ) {
                    if( destination instanceof ISQLDataSet<?> ) {
                        ISQLDataSet<?> ds = (ISQLDataSet<?>)destination;
                        failure = cleanup(failure, () -> ds.removeFilter(filterId));
                    }
                    else if( destination instanceof SQLTreeDataSet<?> ) {
                        SQLTreeDataSet<?> ds = (SQLTreeDataSet<?>)destination;
                        failure = cleanup(failure, () -> ds.removeFilter(filterId));
                    }
                }
            } finally {
                destination = null;
                source = null;
                requeryIf = () -> false;
                pairsColumn = null;
            }
            throwCleanupFailure(failure);
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
            if (e.isAfter() && e.getEventType() == TreeDataSetItemEvent.ItemEventType.CHANGE_VALUE
                    && source instanceof ITreeDataSet
                    && e.getTreeItem() == ((ITreeDataSet<?>)source).getCurrentItem())
                refreshDestination(e.getNewValue());
        }

        @Override
        public void navigated( TreeDataSetNavigationEvent<P> e ) {
            refreshDestination( e.getNewItem() == null ? null : e.getNewItem().getValue() );
        }

        @Override
        public void dataSetChanged( TreeDataSetEvent<P> e )
        {
            if( e.isBefore() && e.getEventType() == DataSetEvent.DataSetEventType.CLOSE ) {
                close();
                return;
            }
            if( e.isAfter()
                && e.getEventType() == DataSetEvent.DataSetEventType.EXECUTE
                && e.getTreeDataSet().isEmpty()
            )
                refreshDestination( null );
        }

        @Override
        public void dataCallChanged(DataCallEvent event) {
            if (source == null)
                return;
            if (event.getEventType() == DataCallEvent.Type.CLOSE && event.isBefore())
                close();
            else if (event.getEventType() == DataCallEvent.Type.EXECUTE && event.isAfter()
                    && Boolean.TRUE.equals(event.getExecutionSuccess()))
                refreshDestination((P)source);
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

    /** Связь дерева с ведомым SQL-деревом. */
    private static class Tree2TreeLink<P,T>
            extends AbstractLink<ITreeDataSet<P>,SQLTreeDataSet<?>,P,T> {

        Tree2TreeLink(ITreeDataSet<P> source, SQLTreeDataSet<?> destination,
                      Supplier<Boolean> requeryIf, List<Triplet<Function<P,T>,String,Boolean>> columns) {
            super(source, destination, requeryIf, columns);
        }
    }

    /** Связь результата успешного вызова с ведомым SQL DataSet. */
    private static class DataCall2DataSetLink<P,T>
            extends AbstractLink<IDataCall,ISQLDataSet<?>,P,T> {

        DataCall2DataSetLink(IDataCall source, ISQLDataSet<?> destination,
                             Supplier<Boolean> requeryIf,
                             List<Triplet<Function<P,T>,String,Boolean>> columns) {
            super(source, destination, requeryIf, columns);
        }
    }

    /** Связь результата успешного вызова с ведомым SQL-деревом. */
    private static class DataCall2TreeLink<P,T>
            extends AbstractLink<IDataCall,SQLTreeDataSet<?>,P,T> {

        DataCall2TreeLink(IDataCall source, SQLTreeDataSet<?> destination,
                          Supplier<Boolean> requeryIf,
                          List<Triplet<Function<P,T>,String,Boolean>> columns) {
            super(source, destination, requeryIf, columns);
        }
    }

    /** Связь двух DataSet с сохранением контракта IDataSetLink. */
    private static class DataSet2DataSetLink<P,T> implements IDataSetLink<P>,
            IDataSetNavigationListener<P>, IDataSetRowListener<P>, IDataSetListener {

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
                if (source instanceof SQLDataSet<?>)
                    source.addDataSetListener(this);
                P row = source.getCurrentRow();
                if (row != null)
                    refreshDestination(row);
            } catch (RuntimeException | Error failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure)
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

        @Override public void dataSetChanged(DataSetEvent event) {
            if (event.isBefore() && event.getEventType() == DataSetEvent.DataSetEventType.CLOSE)
                close();
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
            Throwable failure = null;
            try {
                IDataSet<P> ds = source;
                ISQLDataSet<?> detail = destination;
                failure = cleanup(failure, () -> ds.removeNavigationListener(this));
                failure = cleanup(failure, () -> ds.removeRowListener(this));
                if (ds instanceof SQLDataSet<?>)
                    failure = cleanup(failure, () -> ds.removeDataSetListener(this));
                if (filterId != 0)
                    failure = cleanup(failure, () -> detail.removeFilter(filterId));
            } finally {
                source = null;
                destination = null;
                requeryIf = () -> false;
            }
            throwCleanupFailure(failure);
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
                        IDataSet<P> currentSource = this.source;
                        if (currentSource == null)
                            return null;
                        P row = currentSource.getCurrentRow();
                        return row == null ? null : column.first.apply(row);
                    });
                source.addNavigationListener(this);
                source.addRowListener(this);
                source.addDataSetListener(this);
            } catch (Throwable failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure)
                        failure.addSuppressed(cleanupFailure);
                }
                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on build link between DataSet and DataCall", failure);
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
                Throwable failure = cleanup(null, destination::close);
                throwCleanupFailure(cleanup(failure, this::close));
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
            Throwable failure = null;
            try {
                IDataSet<P> ds = source;
                failure = cleanup(failure, () -> ds.removeNavigationListener(this));
                failure = cleanup(failure, () -> ds.removeRowListener(this));
                failure = cleanup(failure, () -> ds.removeDataSetListener(this));
            } finally {
                source = null;
                requeryIf = () -> false;
            }
            throwCleanupFailure(failure);
        }
    }

    /** Связь дерева с вызовом: параметры берутся из текущего элемента при исполнении. */
    private static class Tree2CallLink<P,T> implements IDataLink<ITreeDataSet<P>,IDataCall>,
            ITreeDataSetNavigationListener<P>, ITreeDataSetItemListener<P>,
            ITreeDataSetListener<P>, AutoCloseable {

        private ITreeDataSet<P> source;
        private final IDataCall destination;
        private Supplier<Boolean> requeryIf;

        Tree2CallLink(ITreeDataSet<P> source, IDataCall destination,
                      Supplier<Boolean> requeryIf, List<Triplet<Function<P,T>,String,Boolean>> columns) {
            this.source = source;
            this.destination = destination;
            this.requeryIf = requeryIf == null ? () -> true : requeryIf;
            try {
                for (Triplet<Function<P,T>,String,Boolean> column : columns)
                    destination.setValueCallback(column.second, () -> {
                        ITreeDataSet<P> currentSource = this.source;
                        if (currentSource == null)
                            return null;
                        ITreeDataSetItem<P> item = currentSource.getCurrentItem();
                        P row = item == null ? null : item.getValue();
                        return row == null ? null : column.first.apply(row);
                    });
                source.addNavigationListener(this);
                source.addItemListener(this);
                source.addDataSetListener(this);
            } catch (Throwable failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure)
                        failure.addSuppressed(cleanupFailure);
                }
                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on build link between TreeDataSet and DataCall", failure);
            }
        }

        @Override public ITreeDataSet<P> getSource() { return source; }
        @Override public IDataCall getDestination() { return destination; }

        @Override public void navigated(TreeDataSetNavigationEvent<P> event) { refresh(); }
        @Override public void itemChanged(TreeDataSetItemEvent<P> event) {
            if (event.isAfter() && event.getEventType() == TreeDataSetItemEvent.ItemEventType.CHANGE_VALUE
                    && source != null && event.getTreeItem() == source.getCurrentItem())
                refresh();
        }

        @Override public void dataSetChanged(TreeDataSetEvent<P> event) {
            if (event.isBefore() && event.getEventType() == DataSetEvent.DataSetEventType.CLOSE) {
                Throwable failure = cleanup(null, destination::close);
                throwCleanupFailure(cleanup(failure, this::close));
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
            Throwable failure = null;
            try {
                ITreeDataSet<P> tree = source;
                failure = cleanup(failure, () -> tree.removeNavigationListener(this));
                failure = cleanup(failure, () -> tree.removeItemListener(this));
                failure = cleanup(failure, () -> tree.removeDataSetListener(this));
            } finally {
                source = null;
                requeryIf = () -> false;
            }
            throwCleanupFailure(failure);
        }
    }

    /** Ведомый вызов использует выходные параметры последнего выполнения ведущего. */
    private static class DataCall2CallLink<P,T> implements IDataLink<IDataCall,IDataCall>,
            IDataCallListener, AutoCloseable {

        private IDataCall source;
        private final IDataCall destination;
        private Supplier<Boolean> requeryIf;
        private final AtomicBoolean forwarding = new AtomicBoolean();

        DataCall2CallLink(IDataCall source, IDataCall destination,
                          Supplier<Boolean> requeryIf,
                          List<Triplet<Function<P,T>,String,Boolean>> columns) {
            if (source == destination)
                throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "DataCall cannot be linked to itself");
            this.source = source;
            this.destination = destination;
            this.requeryIf = requeryIf == null ? () -> true : requeryIf;
            try {
                for (Triplet<Function<P,T>,String,Boolean> column : columns)
                    destination.setValueCallback(column.second, () -> {
                        IDataCall currentSource = this.source;
                        return currentSource == null ? null : column.first.apply((P)currentSource);
                    });
                source.addDataCallListener(this);
            } catch (Throwable failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure)
                        failure.addSuppressed(cleanupFailure);
                }
                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on build link between DataCalls", failure);
            }
        }

        @Override public IDataCall getSource() { return source; }
        @Override public IDataCall getDestination() { return destination; }

        @Override public void dataCallChanged(DataCallEvent event) {
            if (source == null)
                return;
            if (event.getEventType() == DataCallEvent.Type.CLOSE && event.isBefore()) {
                Throwable failure = cleanup(null, this::close);
                throwCleanupFailure(cleanup(failure, destination::close));
            } else if (event.getEventType() == DataCallEvent.Type.EXECUTE && event.isAfter()
                    && Boolean.TRUE.equals(event.getExecutionSuccess())) {
                try {
                    if (!Boolean.TRUE.equals(requeryIf.get()))
                        return;
                    if (!forwarding.compareAndSet(false, true))
                        throw new IllegalStateException(Tags.PRODUCT_LABEL + "Cyclic DataCall link execution");
                    try {
                        destination.execute();
                    } finally {
                        forwarding.set(false);
                    }
                } catch (Throwable failure) {
                    throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on execute dependent DataCall", failure);
                }
            }
        }

        @Override public void setAutoRequery(Supplier<Boolean> value) {
            requeryIf = Objects.requireNonNull(value, "'requeryIf' is null");
        }

        @Override public void close() {
            if (source == null)
                return;
            Throwable failure = null;
            try {
                IDataCall call = source;
                failure = cleanup(failure, () -> call.removeDataCallListener(this));
            } finally {
                source = null;
                requeryIf = () -> false;
            }
            throwCleanupFailure(failure);
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

        if( source instanceof ITreeDataSet && destination instanceof SQLTreeDataSet )
        {
            return (IDataLink<S,D>)new Tree2TreeLink<>(
                    (ITreeDataSet<P>)source, (SQLTreeDataSet<?>)destination,
                    requeryIf, new ArrayList<>(pairsColumn));
        }

        if( source instanceof IDataCall && destination instanceof ISQLDataSet )
        {
            return (IDataLink<S,D>)new DataCall2DataSetLink<>(
                    (IDataCall)source, (ISQLDataSet<?>)destination,
                    requeryIf, new ArrayList<>(pairsColumn));
        }

        if( source instanceof IDataCall && destination instanceof SQLTreeDataSet )
        {
            return (IDataLink<S,D>)new DataCall2TreeLink<>(
                    (IDataCall)source, (SQLTreeDataSet<?>)destination,
                    requeryIf, new ArrayList<>(pairsColumn));
        }

        if( source instanceof IDataCall && destination instanceof IDataCall )
        {
            for (Triplet<Function<P,T>,String,Boolean> column : pairsColumn) {
                if (ru.inversion.utils.S.isNullOrEmpty(column.second))
                    throw new IllegalArgumentException("'destinationParam' is null or empty");
                Objects.requireNonNull(column.first, "'sourceValue' is null");
            }
            return (IDataLink<S,D>)new DataCall2CallLink<>(
                    (IDataCall)source, (IDataCall)destination,
                    requeryIf, new ArrayList<>(pairsColumn));
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

        if( source instanceof ITreeDataSet && destination instanceof IDataCall )
        {
            for (Triplet<Function<P,T>,String,Boolean> column : pairsColumn) {
                if (ru.inversion.utils.S.isNullOrEmpty(column.second))
                    throw new IllegalArgumentException("'destinationParam' is null or empty");
                Objects.requireNonNull(column.first, "'sourceValue' is null");
            }
            return (IDataLink<S,D>)new Tree2CallLink<>(
                    (ITreeDataSet<P>)source, (IDataCall)destination, requeryIf, new ArrayList<>(pairsColumn));
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
