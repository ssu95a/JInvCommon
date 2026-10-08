package ru.inversion.dataset;

import ru.inversion.datacall.DataCallEvent;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.IDataCallListener;
import ru.inversion.tds.*;
import ru.inversion.utils.Pair;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.util.ArrayList;
import java.util.List;
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
        private List< Pair< Function<P,T>,String> > pairsColumn;

        /** */
        public AbstractLink( S source, D destination, Supplier<Boolean> requeryIf, List<Pair<Function< P, T >, String> > pairsColumn )
        {
            this.source      = source;
            this.destination = destination;
            this.requeryIf   = requeryIf;
            this.pairsColumn = pairsColumn;

            // Добавляем маркер для того, чтобы можно было выделить из общего предиката,
            // тот который устанавливается из родительского dataSet
            final StringBuilder sb = new StringBuilder("/*P[*/(");

            final int l = sb.length();

            if( destination instanceof SQLDataSet<?> )
            {
                final SQLDataSet<?> sqds = (SQLDataSet<?>)destination;

                pairsColumn.forEach((p) -> {
                    if( sb.length() > l )
                        sb.append( " and " );
                    sb.append( p.second).append( "= :" ).append( p.second );

                    sqds.setParameter( p.second, null );

                });
                sb.append(")/*P]*/");
                sqds.setFilter( sb.toString(), true, true );
            }
            else if( destination instanceof SQLTreeDataSet )
            {
                final SQLTreeDataSet<?> sqds = (SQLTreeDataSet<?>)destination;

                pairsColumn.forEach(( p) -> {
                    if( sb.length() > l )
                        sb.append( " and " );
                    sb.append( p.second).append( "= :" ).append( p.second );

                    sqds.setParameter( p.second, null );
                });
                sb.append(")/*P]*/");

                sqds.setFilter( sb.toString(), true, true );
            }

            if( source instanceof IDataSet )
            {
                final IDataSet<P> ds = (IDataSet<P>)source;

                ds.addNavigationListener(this);
                ds.addRowListener(this);

                P row = ds.getCurrentRow();

                if( row != null )
                    refreshDestination( ds.getCurrentRow());
            }
            else if( source instanceof ITreeDataSet )
            {
                final ITreeDataSet<P> ds = (ITreeDataSet<P>)source;

                ds.addNavigationListener(this);
                ds.addItemListener(this);

                P row = U.callIfNotNull( ds.getCurrentItem(), ITreeDataSetItem::getValue );

                if( row != null )
                    refreshDestination(row);
            }
            /*
            else if( source instanceof IDataCall ) {
                IDataCall ds = (IDataCall)source;
                ds.addDataCallListener(new IDataCallListener() {
                    @Override
                    public void dataCallChanged( DataCallEvent e ) {
                        if( e.isAfter() )

                    }
                });
            }*/
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

                if( destination instanceof SQLDataSet )
                {
                    final SQLDataSet<?> sqds = (SQLDataSet<?>)destination;

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
                                 Supplier< Boolean > requeryIf, List< Pair< Function< P,T >, String > > pairsColumn ) {
            super( source, destination, requeryIf, pairsColumn );
        }
    }

    /** */
    private static class Tree2DataSetLink<P,T>
            extends AbstractLink<ITreeDataSet<P>,ISQLDataSet<?>,P,T>
    {
        public Tree2DataSetLink( ITreeDataSet<P> source, ISQLDataSet<?> destination,
                                 Supplier< Boolean > requeryIf, List< Pair< Function< P, T >, String > > pairsColumn ) {
            super(source, destination, requeryIf, pairsColumn);
        }
    }


   /**
    * Данные Builder'a
    */
    private S source;
    private D destination;
    private Supplier<Boolean> requeryIf;
    /** */
    private final List< Pair< Function<P,T>,String> > pairsColumn = new ArrayList<>();
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
        pairsColumn.add( Pair.makePair( sourceValue, destinationColumn ));
        return this;
    }

    /**
     * Создает объект связи между 2-мя DataSet
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
            final DataLinkBuilder<P> dlb = new DataLinkBuilder<>();
            dlb.source( (IDataSet<? extends P>) source ).destination( (ISQLDataSet<?>) destination);
            pairsColumn.forEach( (c)->dlb.joinColumn((Function< P, Object >)c.first, c.second ) );

            return (IDataLink<S,D>)dlb.build();
        }

        if( source instanceof IDataSet && destination instanceof SQLTreeDataSet )
        {
            final DataSet2TreeLink<P,T> dlb = new DataSet2TreeLink<P,T>( (IDataSet<P>)source, (SQLTreeDataSet<?>) destination, this.requeryIf, pairsColumn );
            return (IDataLink< S, D >)dlb;
        }

        if( source instanceof ITreeDataSet && destination instanceof ISQLDataSet )
        {
            final Tree2DataSetLink<P,T> dlb = new Tree2DataSetLink<P,T>( (ITreeDataSet<P>)source, (ISQLDataSet<?>) destination, this.requeryIf, pairsColumn );
            return (IDataLink< S, D >)dlb;
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
