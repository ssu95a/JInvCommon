package ru.inversion.datacall;

import ru.inversion.dataset.DataSetEvent;
import ru.inversion.dataset.IDataLink;
import ru.inversion.dataset.IDataSet;
import ru.inversion.dataset.IDataSetListener;
import ru.inversion.utils.Pair;
import ru.inversion.utils.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static ru.inversion.dataset.DataSetEvent.DataSetEventType.CLOSE;
import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.REFRESH;

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
        this.requeryIf = requeryIf;
        return this;
    }

    /** */
    public <T> DataCallLinkBuilder join( Function<T,Object> sourceValue, String destinationParam ) {
        pairsColumn.add( Pair.makePair( sourceValue, destinationParam ));
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

        return null;
    }

    /** */
    private <R> IDataLink d2cBuild( ) {

        return new IDataLink<IDataSet<R>,IDataCall>() {

            Supplier< Boolean > requeryIf = DataCallLinkBuilder.this.requeryIf;

            final IDataSet<R> ds = (IDataSet<R>)source;
            final IDataCall dc = (IDataCall)destination;

            {
                try {

                    for( Pair< Function, String > p : pairsColumn )
                         //dc.setValueCallback( p.second, () -> p.first.apply( ds.getCurrentRow() ));
                        dc.setValueCallback( p.second, new Supplier< Object >() {
                            @Override
                            public Object get() {
                                final R currentRow = ds.getCurrentRow();
                                if( currentRow == null )
                                    return null;
                                else
                                    return p.first.apply( currentRow );
                            }
                        });

                    ds.addNavigationListener( (e)-> refresh( e.getNewRow(), dc) );
                    ds.addRowListener       ( (e)-> {
                        if( e.getRowOperation() == REFRESH )
                            refresh( e.getNewRow(), dc );
                    });
                    ds.addDataSetListener( new IDataSetListener() {
                        @Override
                        public void dataSetChanged( DataSetEvent e ) {
                            if( e.isBefore() && e.getEventType() == CLOSE )
                                dc.close();
                        }
                    });

                }catch( Throwable th ) {
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on build link between DataSet and DataCall", th );
                }
            }

            /** */
            private void refresh( R row, IDataCall dataCall ) {

                try {

                    if( requeryIf.get() )
                        dataCall.execute();
                }
                catch( Throwable th ) {
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on execute dataCall", th );
                }
            }
            /** */
            @Override
            public IDataSet getSource() {
                return ds;
            }
            /** */
            @Override
            public IDataCall getDestination() {
                return dc;
            }
            /** */
            @Override
            public void setAutoRequery( Supplier< Boolean > requeryIf ) {
                this.requeryIf = requeryIf;
            }
        };
    }
}
