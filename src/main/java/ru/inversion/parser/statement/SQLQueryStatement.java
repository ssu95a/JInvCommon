package ru.inversion.parser.statement;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ParametersByIndex;
import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.parser.maker.SQLMaker_Query;
import ru.inversion.parser.node.INode;
import ru.inversion.parser.node.NodeTypeEnum;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static ru.inversion.parser.maker.AbstractSQLMakerBase.createSQLMaker;
import static ru.inversion.parser.statement.SQLQueryStatement.PredicateTypeEnum.PERMANENT_FILTER;
import static ru.inversion.parser.statement.SQLQueryStatement.PredicateTypeEnum.TEMPORARY_FILTER;

/** */
public class SQLQueryStatement {

    /** */
    public enum PredicateTypeEnum {

        PREDICATE,

        TEMPORARY_FILTER,

        PERMANENT_FILTER,

        AUTO_PREDICATE
    }

    /**
     * Генератор идентификаторов для предикатов.
     */
    final static private AtomicInteger g_predicateIDSequence = new AtomicInteger(0);

    /** */
    protected class PredicateItem {

        final private String text,
                              tag;

        private boolean pre = true;

        /** */
        final private PredicateTypeEnum type;

        /** */
        public PredicateItem( PredicateTypeEnum type, String text, String tag ) {
            this.text = text;
            this.tag  = tag;
            this.type = type;
        }

        /** */
        public boolean isPre() {
            return pre;
        }

        /** */
        public  boolean isFinal() {
            return !pre;
        }

        /** */
        public  boolean isFilter() {
            return U.in( type, TEMPORARY_FILTER, PERMANENT_FILTER );
        }

        /** */
        public  void makeFinal() {
            this.pre = false;
        }

        /** */
        public String getText() {
            return text;
        }

        /** */
        public String getTag() { return tag; }

        /** */
        public PredicateTypeEnum getType() {
            return type;
        }
    }

    /** */
    final private String query;

    /** */
    protected String orderBy; //defaultOrderBy;

    /** */
    protected String markStatement;

    /** */
    protected String alias;

    /** */
    protected String optimizerHint;

    /** */
    transient private String completedQuery;

    /** */
    final protected Map<Integer, PredicateItem> predicates = new TreeMap<>();

    /** */
    public SQLQueryStatement( String query ) {
        this.query = Objects.requireNonNull( query, "'query' is null" );
    }

    /** */
    public String getQuery( ) { return query; }

    /** */
    public int appendPredicate( PredicateTypeEnum predicateType, String predicate, String tag ) {

        String newPredicate = ( predicate == null ? null : predicate.trim() );

        if( S.isNullOrEmpty( newPredicate )  )
            return 0;

        int predicateId = g_predicateIDSequence.incrementAndGet();

        predicates.put (
            predicateId,
            new PredicateItem( predicateType, newPredicate, tag )
        );

        return predicateId;
    }

    /** */
    private boolean removePredicate( Predicate<Map.Entry<Integer, PredicateItem>> predicate ) {
        return predicates.entrySet().removeIf( predicate );
    }

    /** */
    public boolean removePredicate( int ... Ids ) {

        if( Ids.length >0 ) {

            if( Ids.length == 1 )
                return removePredicate( (e)->e.getKey() == Ids[0] );
                //return predicates.remove( Ids[0] ) != null;

            return removePredicate( (e)->U.inInt( e.getKey(), Ids ) );

            //return predicates.keySet().removeAll( Arrays.asList(Ids) );
        }

        return false;
    }

    /** */
    public boolean removePredicate( String ... tags ) {

        if( tags.length >0 )
            //return predicates.entrySet().removeIf( (e)-> U.in( e.getValue().getTag(), tags ) );
            return removePredicate( (e)->U.in( e.getValue().getTag(), tags ) );

        return false;
    }

    /** */
    public boolean removePredicate( PredicateTypeEnum type ) {
        return removePredicate( (e)->e.getValue().getType() == type );
        //return predicates.entrySet().removeIf( (e)-> e.getValue().getType() == type );
    }

    /** */
    public boolean removePrePredicates( ) {
        return removePredicate( (e)->e.getValue().isPre() );
        //return predicates.entrySet().removeIf( (e)-> e.getValue().isPre() );
    }

    /** */
    public void fixed( ) {
        predicates.values( ).stream( ).filter( (f)->f.isPre( ) ).forEach( PredicateItem::makeFinal );
    }

    /** */
    public void undo( ) {
        predicates.values( ).stream( ).filter( (f)->f.isPre( ) ).forEach( PredicateItem::makeFinal );
    }

    /** */
    public boolean setOrderBy ( String ob ) {

        String newOrderBYExpression = ( ob == null ? null : ob.trim() );

        if( S.isNullOrEmpty( newOrderBYExpression )  )
            newOrderBYExpression = null;

        if( !U.equals( newOrderBYExpression, this.orderBy ) )
        {
            this.orderBy = newOrderBYExpression;

            //needReCreateReader();
            return true;
        }
        return false;
    }

    /** */
    public String getOrderBy( ) {
        return orderBy;
    }

    /** */
    public boolean setQueryAlias( String a ) {

        String newAlias = ( a == null ? null : a.trim() );

        if( S.isNullOrEmpty( newAlias )  )
            newAlias = null;

        if( !U.equals( newAlias, this.alias ) )
        {
            this.alias = newAlias;

            //needReCreateReader();
            return true;
        }
        return false;
    }

    /** */
    public String getQueryAlias( ) {
        return this.alias;
    }

    /** */
    public boolean setMarkColumn( String m ) {

        String newMarkStatement = ( m == null ? null : m.trim() );

        if( S.isNullOrEmpty( newMarkStatement )  )
            newMarkStatement = null;

        if( !U.equals( newMarkStatement, this.markStatement ) )
        {
            this.markStatement = newMarkStatement;

            //needReCreateReader();
            return true;
        }
        return false;
    }

    /** */
    public String getCompletedWherePredicate( ) {
        return this.completedQuery;
    }

    /** */
    public String prepareSql( BiConsumer<INode, PrepareContext > sqlMaker, IParameters callbackParameters, List<Object> parameters ) {

        return null;
    }

    /** */
    public String prepareSql( PrepareModeEnum mode, IParameters callbackParameters, List<Object> parameters ) {
        return prepareSql( createSQLMaker( mode ), callbackParameters, parameters );
    }

    /** */
    public String prepareAggrSql( List<Pair<String, AggrFuncEnum> > aggrList, IParameters callbackParameters, List<Object> parameters ) {
        return null;
    }

    /** */
    public static void main(String[] args) {

        try {

            String test_SQL = "SELECT iTrnNum ,iTrnANum,iTrnType, " +
                    "CASE WHEN iTrnType = ? AND cTrnCur != cTrnCurC THEN PRDOC.Get_Report_Name (?, Nvl(dTrnDoc,dTrnCreate)) " +
                    "ELSE PRDOC.Get_Report_Name (iTrnType,Nvl(dTrnDoc,dTrnCreate),Case When iTrnType In (?,?,?) " +
                    "And Nvl(cTrnVo,IDOC_UTIL.Get_DefaultVO(iTrnType)) = ? then " +
                    "KO_AP.IsAccCash(cTrnAccD,cTrnCur,?,?) + KO_AP.IsAccCash(cTrnAccC,cTrnCurC,?,?) Else ? End) END " +
                    "AS cReportName FROM TRN_MF where /**/ (TRN_MF.iTrnNum, TRN_MF.iTrnANum) " +
                    "IN (SELECT IDRow, IDARow FROM MRK_ID2 WHERE IDMarker=?) AND iTrnType NOT IN (?, ?, ?)\n" +
                    "order by iTrnNum, iTrnANum";

            String test_SQL1 = "SELECT CASE GROUPING (cAblCur) WHEN 1 THEN '***' ELSE cAblCur END cAblCur,"
                    +" SUM(mABLfdbaldeb) mABLfdbaldeb, SUM(mABLfdbalcred) mABLfdbalcred "
                    +" FROM ABL GROUP BY ROLLUP (cAblCur)";

            String test_SQL2 = "SELECT 1, 2, 3, user FROM ABL where null is null GROUP BY ROLLUP (cAblCur)";

            //Несколько ORDER BY
            String testFour = "select a.LEV,a.CSMRNAME,a.SMR_ID,a.OTD_ID from (\n" +
                    "select 1 as lev, csmrname, idsmr as smr_id, NULL AS otd_id  from smr_mf\n" +
                    "union all \n" +
                    "select 2 as lev, NVL (cotdname, iOtdNum || '-???') AS csmrname, idsmr AS smr_id, iotdnum AS otd_id from otd_mf\n" +
                    "order by 1" +
                    ") a\n"
                    + "where lev=1\n"
                    + "order by 2 , 1, 3 , 4"
                    ;

            String testFive =
                    "select dtrntran BETWEEN TO_DATE( :p1, 'DDMMRR' ) AND TO_DATE( :p1, 'DDMMRR' ) + 1-1/86400 from dual";

            SQLQueryStatement qs = new SQLQueryStatement( testFive );
            //qs.setQuery( "(ITRNSOP=4) and (DTRNDOC>to_date('01-01-2003','DD-MM-YYYY')) order by DTRNDOC" );
            //qs.setQuery( "to_date('01-01-2003','DD-MM-YYYY') order by DTRNDOC" );
            //qs.appendPredicate( PredicateTypeEnum.PREDICATE,"null is null and user = 'XXI' and ~(select 1 from dual)~ is not null", null );
//            qs.appendPredicate( TEMPORARY_FILTER,"count(1) > 0", null );
            //qs.setOrderBy( "a.SMR_ID, 2, 1" );



            //Форматирование
            BiConsumer<INode, PrepareContext > sqlMaker = new SQLMaker_Query() {
                @Override
                public void accept( INode n, PrepareContext c ) {
                    boolean highlight =
                               n.getType() == NodeTypeEnum.SELECT
                            || n.getType() == NodeTypeEnum.FROM
//                            || n.getType() == NodeTypeEnum.WHERE
//                            || n.getType() == NodeTypeEnum.WITH
//                            || n.getType() == NodeTypeEnum.UNION
                            || n.getType() == NodeTypeEnum.ORDER_BY
                            ;

                    if ( highlight ){
                        c.nl();
                        for ( int i = 0; i < n.getLevel(); i++ ) {
                            c.append( '\t' );
                        }
                    }

                    super.accept( n, c );
                }
            };

            List<Object> values = new LinkedList<>();
            String s = qs.prepareSql(sqlMaker, new IParameters() {
                @Override
                public Object getParameter( String parameterName ) {
                    return (ParametersByIndex) (int parameterIndex) -> parameterName;
                }

                @Override
                public Object getParameter( int parameterIndex ) {
                    return null;
                }
            }, values);
            System.out.println( s );
            System.out.println( values );

        } catch (Throwable ex) {
            //JInvErrorService.handleException(null, ex);
            ex.printStackTrace();
        }
    }

}
