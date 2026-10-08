package ru.inversion.datacall;


import ru.inversion.dataset.IParameters;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.parser.token.Token;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.io.StringReader;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static ru.inversion.datacall.BatchBuilder.BatchModeEnum.DELETE;
import static ru.inversion.datacall.BatchBuilder.BatchModeEnum.INSERT;
import static ru.inversion.parser.statement.PrepareModeEnum.QUERY;


/** */
public class
BatchBuilder<T> {
    /** */
    public enum BatchModeEnum {
        INSERT,
        UPDATE, // not Impl
        DELETE,
        MERGE,  // not Impl
        CUSTOM
    };

    /** */
    final public static String TABLE  = "$TABLE$";

    /** */
    private TaskContext tc;

    /** */
    private Class<T> valueClass;

    /** */
    private URL url;

    /** */
    private String name = TABLE;

    /** */
    private IParameters callBackParameters;

    /** только для name = TABLE */
    private BatchModeEnum batchMode = INSERT;

    /** */
    private boolean commitBatch = false;

    /** */
    private boolean continueOnError = false;

    /** */
    private int batchSize = 50;

    /** */
    private BiConsumer<T, PreparedStatement> mapper;

    /** */
    public BatchBuilder( TaskContext tc ) {
        this.tc = tc;
    }

    /** */
    public BatchBuilder<T>  name( String name ) {
        this.name = name;
        return this;
    }
    /** */
    public BatchBuilder<T>  valueClass( Class<T> valueClass ) {
        this.valueClass = valueClass;
        return this;
    }
    /** */
    public BatchBuilder<T>  batchSize( int v ) {
        this.batchSize = v;
        return this;
    }
    /** */
    public BatchBuilder<T> url( URL url ) {
        this.url = url;
        return this;
    }
    /** */
    public BatchBuilder<T> callBackParameters( IParameters v ) {
        this.callBackParameters = v;
        return this;
    }

    /** */
    @Deprecated
    public BatchBuilder<T> insertMode()
    {
        batchMode = INSERT;
        return this;
    }
    /** */
    @Deprecated
    public BatchBuilder<T> mergeMode()
    {
        batchMode = BatchModeEnum.MERGE;
        return this;
    }

    /** */
    public BatchBuilder<T> batchMode( BatchModeEnum v )
    {
        batchMode = v;
        return this;
    }

    /** */
    public BatchBuilder<T> commitBatch( boolean v )
    {
        commitBatch = v;
        return this;
    }
    /** */
    public BatchBuilder<T> continueOnError(boolean v)
    {
        continueOnError = v;
        return this;
    }
    /** */
    public BatchBuilder<T> mapper( BiConsumer<T, PreparedStatement> mapper )
    {
        this.mapper = mapper;
        return this;
    }

    /** */
    private PreparedStatement createPreparedStatement( ) throws CallException {

        try {

            if( batchMode == BatchModeEnum.MERGE || batchMode == BatchModeEnum.UPDATE )
                throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "'" + batchMode + "' mode is not supported yet" );

            final String statementText;

            final IEntityMetaData<T> metaData = EntityMetadataFactory.getEntityMetaData(valueClass);

            if( name.equals(TABLE) )
            {
                StringBuilder sb = new StringBuilder();

                final List<IEntityProperty<T,?>> columnList = new ArrayList<>();

                if( batchMode == INSERT )
                {
                    sb.append("insert into ").append( metaData.getTableName() ).append(" (");

                    for( IEntityProperty<T,?> p : metaData.getColumnsMap().values() )
                    {
                        if( !p.getColumnInfo().isInsertable() )
                            continue;

                        columnList.add(p);

                        if( columnList.size() > 1 )
                            sb.append(',');

                        sb.append( p.getColumnInfo().getName() );

                    }//end for

                    sb.append(") values( ");
                    columnList.forEach( (c)->sb.append("?,") );
                    sb.setCharAt( sb.length() - 1, ' ' );
                    sb.append(")");

                }//end INSERT
                else if( batchMode == BatchModeEnum.DELETE )
                {
                    sb.append("delete from ").append( metaData.getTableName() ).append(" where ");

                    for( IEntityProperty<T,?> p : metaData.getIDList() )
                    {
                        columnList.add(p);

                        if( columnList.size() > 1 )
                            sb.append(" and ");

                        sb.append( p.getColumnInfo().getName() ).append("=?");

                    }//end for
                }

                if( mapper == null )
                    mapper = new BiConsumer< T, PreparedStatement >() {
                        @Override
                        public void accept( T t, PreparedStatement ps ) {

                            try {

                                if( t == null )
                                    return;

                                int i = 1;

                                for( IEntityProperty<T,?> p : columnList )
                                     ps.setObject( i++, Jt2St.convert( p.invokeGetter(t) ));
                            }
                            catch( Throwable th ) {
                                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on map entity '" + t.getClass() + "' to JDBC PreparedStatement", th );
                            }
                        }
                    };

                statementText = sb.toString();

            }
            else
            {
                if ( batchMode == BatchModeEnum.CUSTOM )
                {
                    final CallStatement cs = SQLCallBuilder.NEW(tc).url(url).name(name).buildStatement();

                    Map<Integer, Function<T,Object> > valuesMap = new TreeMap<>();

                    final String st = cs.prepareSql(AbstractCallMakerBase.createPLSqlMaker(QUERY),
                            ( pos, id, value ) -> {
                                if(id instanceof Integer) {
                                    valuesMap.put(pos, ( t ) -> callBackParameters.getParameter((Integer)id));
                                } else if(id instanceof String) {
                                    final IEntityProperty< T, ? > p = metaData.getProperty((String)id);
                                    valuesMap.put(pos,
                                        p == null ? ( t ) -> callBackParameters.getParameter((String)id) : ( t ) -> p.invokeGetter(t)
                                    );
                                } else if(id instanceof Token.TypeEnum) {
                                    valuesMap.put(pos, ( t ) -> value);
                                }
                            }
                    );

                    mapper = new BiConsumer< T, PreparedStatement >() {
                        @Override
                        public void accept( T t, PreparedStatement ps ) {

                            int    k = -1;
                            Object v = null;

                            try {

                                if( t == null )
                                    return;

                                for( Map.Entry<Integer, Function<T,Object> > e : valuesMap.entrySet() ) {

                                    k = e.getKey();
                                    v = e.getValue().apply(t);
                                    if( S.isString(v) && ((String)v).length() > 500 )
                                        ps.setCharacterStream( k, new StringReader( ((String)v )) );
                                    else
                                        ps.setObject( k,  Jt2St.convert(v) );
                                }
                            }
                            catch( Throwable th ) {
                                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on set parameter value to JDBC PreparedStatement, with key " + k + " and value " + v, th );
                            }
                        }
                    };

                    statementText = st;

                }
                else
                    statementText = null;
            }

            if( statementText == null )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Error on make SQL statementText for JDBC PreparedStatement: Неопределяемые параметры настройки." );

            return tc.getConnection().prepareStatement( statementText );
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on prepare 'prepareStatement'", th );
        }
    }

    /** */
    public IBatchLoader<T> build( ) throws CallException {

        if( valueClass == null )
            throw new IllegalArgumentException("'valueClass' is null");

        if( S.isNullOrEmpty(name) )
            throw new IllegalArgumentException("'name' is null");

        if( U.in( batchMode, INSERT, DELETE ) && name != TABLE  )
            throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "Only 'TABLE' mode support now. Если нужны другие режимы обращайтесь по известному адресу.");

        if( batchMode == BatchModeEnum.CUSTOM && ( this.url == null || S.isNullOrEmpty(name) ) )
            throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "Для режима 'CUSTOM' необходимо установить атрибут 'url' и 'name'");

        return new BatchLoaderImp<T>(
            createPreparedStatement(),
            mapper,
            Math.max( 50, batchSize ),
            commitBatch,
            continueOnError
        );
    }

    static private class BatchLoaderImp<T> implements IBatchLoader<T> {

        /** Коллекция слушателей событий самого DataCall */
        final private IListenerManEvent<IBatchListener, BatchEvent> listeners = ListenerManFactory.createListenerManEvent( IBatchListener::batchOperation );
        /** */
        private Iterator<T> iter;
        /** */
        private PreparedStatement ps;
        /** */
        final private BiConsumer<T,PreparedStatement> mapper;
        /** */
        final private int batchSize;
        /** */
        final private boolean commitBatch;
        /** */
        final private boolean continueOnError;

        /** */
        private BatchLoaderImp(
                PreparedStatement ps,
                BiConsumer< T, PreparedStatement > mapper,
                int batchSize, boolean commitBatch, boolean continueOnError )
        {
            this.ps        = ps;
            this.mapper    = mapper;
            this.batchSize = batchSize;
            this.commitBatch = commitBatch;
            this.continueOnError = continueOnError;
        }

        @Override
        public BatchLoaderImp iterator( Iterator iter ) throws CallException {
            this.iter = iter;
            return this;
        }

        @Override
        public BatchLoaderImp execute() throws CallException {

            try {

                if( iter == null )
                    throw new IllegalStateException( Tags.PRODUCT_LABEL + "Iterator 'iter' is null" );

                fireBatchEvent( BatchEvent.Type.EXECUTE, true, 0, null );

                int itemCount = 0;
                int batchCount= 0;
                int count     = 0;

                while( iter.hasNext() )
                {
                    T t = iter.next();

                    fireBatchEvent( BatchEvent.Type.PREPARE_ITEM, true, itemCount, t );

                    try {

                        mapper.accept( t, ps );

                        ps.addBatch();

                        fireBatchEvent( BatchEvent.Type.PREPARE_ITEM, false, itemCount, t );
                    }
                    catch( Throwable th ) {
                        if( continueOnError )
                        {
                            fireThrowableEvent( BatchEvent.Type.PREPARE_ITEM, itemCount, t, th );
                            continue;
                        }
                        else
                            throw th;
                    }

                    count++;

                    if( count == batchSize || !iter.hasNext() )
                    {
                        count = 0;

                        try {

                            fireBatchEvent( BatchEvent.Type.PROCESS_BATCH, true,  batchCount, null );
                            ps.executeBatch();
                            fireBatchEvent( BatchEvent.Type.PROCESS_BATCH, false, batchCount, null );

                            if( commitBatch )
                                ps.getConnection().commit();
                        }
                        catch( Throwable th ) {

                            if( commitBatch )
                                ps.getConnection().rollback();

                            if( continueOnError )
                            {
                                fireThrowableEvent( BatchEvent.Type.PROCESS_BATCH, batchCount, null, th );
                                continue;
                            }
                            else
                                throw th;
                        }

                        batchCount++;
                    }

                    itemCount++;

                }//end while

                fireBatchEvent( BatchEvent.Type.EXECUTE, false, 0, null );
            }
            catch( Throwable th ) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on execute batch loader", th );
            }
            return this;
        }

        private void fireBatchEvent( BatchEvent.Type eventType, boolean before,  int dataIndex, T item ) {
            if(!listeners.isEmpty() )
                listeners.fire( new BatchEvent( this, eventType, before, dataIndex, item, null ) );
        }

        private void fireThrowableEvent( BatchEvent.Type eventType, int dataIndex, T item, Throwable th ) {
            if(!listeners.isEmpty() )
                listeners.fire( new BatchEvent( this, eventType, false, dataIndex, item, th ) );
        }

        @Override
        public BatchLoaderImp addBatchListener( IBatchListener l ) throws CallException {
            listeners.addListener(l);
            return this;
        }

        @Override
        public BatchLoaderImp removeBatchListener( IBatchListener l ) throws CallException {
            listeners.removeListener(l);
            return this;
        }

        @Override
        public void close() {

            if( ps != null )
            {
                fireBatchEvent( BatchEvent.Type.CLOSE, true, 0, null );

                try {

                    if( !ps.isClosed() )
                         ps.close();

                    ps = null;
                }
                catch( SQLException ignored ) {
                }

                fireBatchEvent( BatchEvent.Type.CLOSE, false, 0, null );
            }
        }
    }

    }
