package ru.inversion.dataset.parser.anlzr;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import ru.inversion.dataset.parser.ExprInfo;
import ru.inversion.dataset.parser.SQLItem;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

/**
 *
 * @author ssu
 */
public class SQLAnalyzerHelper {

    private class Context {

        final private String s[] = new String[] { null, null, null };

        public Context( String s1, String s2 ) {
            s[1] = s1 == null ? null : s1.toUpperCase();
            s[2] = s2 == null ? null : s2.toUpperCase();;
        }

        /** */
        public String current( ) {
            return s[1];
        }

        /** */
        public String next( ) {
            return s[2];
        }

        /** */
		public String previous ( ) {
            return s[0];
        }

        /** */
        public void move( String next ) {
            s[0] = s[1];
            s[1] = s[2];
            s[2] = next == null ? null : next.toUpperCase();
        }
    }

    /** */
    private abstract class AbstractState {
        /** */
        public abstract boolean process( Context context );
        /** */
        public abstract void finish( ExprInfo exprInfo );
    }

    /** */
    private class WordState extends AbstractState {

        private boolean hasOrderBy = false;
        private boolean hasWhere   = false;

        /** */
        public boolean process( Context context ) {

            if( "FROM".equals( context.current() ) ) {
                return false;
            }

            if( "ORDER".equals( context.current() ) && "BY".equals( context.next() ) )
                hasOrderBy = true;

            if( "WHERE".equals( context.current() ) )
                hasWhere = true;

            return true;
        }

        /** */
        public void finish( ExprInfo exprInfo ) {
            if( !exprInfo.isHasOrderBy() )
                 exprInfo.setHasOrderBy( hasOrderBy );
            if( !exprInfo.isHasWhere() )
                 exprInfo.setHasWhere( hasWhere );
        }
    }

    /** */
    private class FromState extends AbstractState {

        private List<String> tables;

        /** */
        public boolean process( Context context ) {

            if( "FROM".equals( context.current() ) ) {

                if( tables != null )
                    throw new IllegalStateException("tables is not null");

                tables = new ArrayList<String>();
            }
            else
            {
                try {

                    if( U.in( context.current(), "WHERE", "HAVING") )
                        return false;

                    if( "ORDER".equals( context.current() ) && "BY".equals( context.next() ) )
                        return false;

                    if( "GROUP".equals( context.current() ) && "BY".equals( context.next() ) )
                        return false;

                    if( "CONNECT".equals( context.current() ) && "BY".equals( context.next() ) )
                        return false;

                } finally {

                }

                tables.add( context.current() );
            }

            return true;
        }

        /** */
        public void finish( ExprInfo exprInfo ) {

            if( tables != null && !tables.isEmpty() && !exprInfo.isMysterious() ) {

                if( tables.size() < 3 ) {
                    exprInfo.setMysterious( false );
                    exprInfo.setTableFrom ( tables.get(0) );
                    if( tables.size() > 1 )
                        exprInfo.setAlias(tables.get(tables.size()-1));
                }
                else
                    exprInfo.setMysterious(true);
            }

            tables = null;
        }
    }

    final private Iterator<SQLItem> iterator;

    public SQLAnalyzerHelper( Iterator<SQLItem> iterator ) {
        this.iterator = Objects.requireNonNull( iterator, "items is null" );
    }

    private List<String> byComma( String s ) {

        StringBuilder sb = null;

        List<String> ls = new ArrayList<>( );

        char ch;

        for( int i = 0; i < s.length(); i++ ) {

             ch = s.charAt(i);

             if( ch == ',' ) {

                 if( sb != null ) {

                     ls.add( sb.toString() );

                     sb = null;
                 }

                 ls.add(",");

             }
             else {

                 if( sb == null )
                     sb = new StringBuilder();

                 sb.append(ch);
             }

        }

        if( sb != null )
            ls.add( sb.toString() );

        return ls;
    }

    /** */
    private void analyzeExpr( String exprText, List<String> words ) {

        if( exprText.charAt(0) == '"' && S.lastChar(exprText) == '"' )
            words.add(exprText);
        else {
            words.addAll(
               Stream.of (
                    exprText.split("\\s+") )
                   .filter ( S::isNotNullOrEmpty )
                   .flatMap( (s)->
                                byComma(s).stream()
                   )
                   //.map    ( String::trim )
                   .collect( Collectors.toList() )
            );
        }
    }

    /** @return  */
    public ExprInfo analyze( ) {

        ExprInfo exprInfo       = new ExprInfo( );

		Iterator<SQLItem> iter  = iterator;
		List<String>      words = new ArrayList<>();

		while( iter.hasNext() ) {

			SQLItem	currentItem = iter.next( );

			switch( currentItem.getType() ) {
				case INDEXED_PARAMETER:
				case NAMED_PARAMETER  :
				case DECIMAL:
				case STRING :
				case INTEGER:
					words.add( currentItem.getValue() == null ? "?" : currentItem.getValue().toString() );
                break;
                case COMMENT:
                {
                    if( currentItem.getValue() != null && currentItem.getValue().toString().startsWith("/*ob#") ) {
                        String s1 = currentItem.getValue().toString();
                        s1 = s1.replace( "/*ob#", " " );
                        s1 = s1.replace( "#ob*/", " " );
                        analyzeExpr( s1, words );
                    }

                }
                break;
                case TEXT_EXPRESSION  :
                    analyzeExpr( currentItem.getValue().toString(), words );
                break;
			}

		}//end while

        boolean mysterious = words.stream()
                                  .filter( (w)->"FROM".equalsIgnoreCase(w)).count() > 1
                                  ||
                             words.stream()
                                  .filter((w)->"WHERE".equalsIgnoreCase(w)).count() > 1
                            ;
                             //|| isSelectAlias( words );

        exprInfo.setMysterious(mysterious);

        if( !mysterious ) {

            WordState ws = new WordState();
            FromState fs = new FromState();
            AbstractState as = ws;

            words.add(null);
            words.add(null);

            Context context = new Context( words.get(0), words.get(1) );

            for( int i = 2; i < words.size(); i++ ) {

                if( !as.process(context) ) {
                     as.finish(exprInfo);
                    as = ( as == ws ? fs : ws );
                    as.process(context);
                }

                context.move( words.get(i) );

            }//end if

            as.finish(exprInfo);
        }

//        int nCountFrom   = 0;
//        int orderByMode  = 0;
//        StringBuilder sbOrderBy = new StringBuilder();
//
//        for( String s : words ) {
//
//            if( orderByMode == 0 ) {
//
//                if( "FROM".equalsIgnoreCase(s)  ) {
//                    nCountFrom ++;
//                    continue;
//                }
//
//                if( "ORDER".equalsIgnoreCase(s) ) {
//                    orderByMode = 1;
//                    continue;
//                }
//            }
//            else {
//
//                if( orderByMode == 1 ) {
//
//                    if( !"BY".equalsIgnoreCase(s) ) {
//                        orderByMode = 0;
//                        continue;
//                    }
//                    orderByMode++;
//                }
//                else
//                    sbOrderBy.append(s);
//            }
//        }
//
//        exprInfo.setFromCount( nCountFrom );
//        exprInfo.setOrderBy  ( sbOrderBy.toString() );

        return exprInfo;
    }

    /** Определяет, используются ли алиасы между SELECT и FROM */
    private boolean isSelectAlias( final List<String> words ) {
        //индекс SELECT
        int select = words.indexOf( words.stream().filter( "SELECT"::equalsIgnoreCase ).findFirst().orElse( null ) );
        //индекс FROM
        int from = words.indexOf( words.stream().filter( "FROM"::equalsIgnoreCase ).findFirst().orElse( null ) );

        if ( select == -1 || from == -1 || select >= from ){
            return false;
        }
        TreeSet<Integer> indexes = new TreeSet<>();

        indexes.add( select );
        indexes.add( from );

        //ищем запятые между ними
        IntStream.range(0, words.size())
                .filter( i -> words.get( i ).equals( "," )
                        && i > select
                        && i < from )
                .forEach( indexes::add );

        //если расстояние между SELECT и ближайшей запятой/между двумя запятыми/последней запятой и FROM > 2,
        // значит есть алиас вида SELECT CNAME, >>AUP_UTIL.Get_Tab_Comment (cname, null) CCOMM<< FROM
        int prevIndex = -1;
        for ( final Integer index : indexes ) {
            if ( prevIndex != -1 && Math.abs( index - prevIndex ) > 2 ){
                return true;
            }
            prevIndex = index;
        }
        return false;
    }

    /** */
    public static void main(String[] args) {

        try {

//               String s = "\"select 1  2 .. from\t\tV_TRN_PART_CURRENT\nTRN";
//
//               for( String s1 : Arrays.asList( s.split("\\s+") ) )
//                    System.out.println( s1 );
//
//               StringTokenizer st = new StringTokenizer(s, " \t\n\r");
//
//               while(st.hasMoreTokens()){
//
//               // Получаем слово и что-нибудь делаем с ним, например,
//
//               // просто выводим на экран
//
//               System.out.println(st.nextToken()) ;
//
//               }
//
//            String s1 = "'%'%'";
//            String s2 = "%'%'";
//            String s3 = "'%%";
//            String s4 = "%%'";
//            String s5 = "%'%";
//            String s6 = "%%";

            SQLParser p = new SQLParser( "SELECT 1, r.IDSMR, r.CACCACC, r.CACCCUR, r.CIDTYPE, r.DACCRESP, r.CIDEDIT, USR.CUSRNAME, r.ROWID\n" +
"	 FROM \"ACC_RESP_H\" r, USR\n" +
"	 WHERE USR.CUSRLOGNAME(+)=r.CIDEDIT\n" +
"         ORDER BY 1, r.DACCRESP DESC, 2 ");

            SQLParser p1 = new SQLParser( "select 12 from dual x order by 1");

            SQLAnalyzerHelper ah = new SQLAnalyzerHelper(p1.iterator());
            ExprInfo ei = ah.analyze();
            //p.analyze( );
            //p.toIterator().forEachRemaining( (s)->System.out.println( s.getValue() ) );
System.out.println("end;");

        } catch (Throwable ex) {
            //JInvErrorService.handleException(null, ex);
            ex.printStackTrace();
        }
    }

}
