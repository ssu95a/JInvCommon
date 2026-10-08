package ru.inversion.dataset.parser;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.parser.anlzr.SQLAnalyzerHelper;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.utils.S;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import java.io.StringReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.inversion.dataset.parser.AbstractState.isDigit;
import static ru.inversion.dataset.parser.SQLItem.ItemTypeEnum.*;
import static ru.inversion.dataset.parser.SQLParser.Lang.PL_SQL;
import static ru.inversion.dataset.parser.SQLParser.Lang.SQL;

/**
 *
 * @author ssu @
 */
public class SQLParser implements Iterable<SQLItem>, ReaderScaner.IScanerListener {

	public enum Lang { SQL, PL_SQL }

	private final ReaderScaner  scaner;
	private final List<SQLItem>	itemList = new LinkedList<>();
    private final Lang          lang;

	private AbstractState	    currentState;
    private boolean             state    = false;

	private boolean				replaceConstants = true;

	/** */
	public SQLParser( String s ) {
        this(s, SQL);
	}

	/** */
	public SQLParser( String s, Lang lang ) {
		this.lang = lang;
		s = commentOrderBy( s );
		scaner = new ReaderScaner( new StringReader(s) , this );
	}

	/** Находим и комментируем последний Order By*/
	 private String commentOrderBy( String s ) {
	 	if ( lang == PL_SQL || S.isNullOrEmpty(s) ){
	 		return s;
		}
		 //Находим текст начиная с Order by до первой закрывающей скобки, union, where, комментария, либо до конца
		 Matcher matcher =
				 Pattern.compile( "(?is)(?:order\\s+(?:siblings\\s+)?by.+?)(?=\\)|--|\\/\\*|\\*\\/|:|;|union\\s|minus\\s|intersect\\s|where\\s|\\Z)" )
						.matcher(s);
		while ( matcher.find() ) {
			String match = matcher.group();
			if(  S.isNotNullOrEmpty(match) ) {
				s = s.replace( match, "/*ob#"+match+"#ob*/" );
			}
		}
		return s;
	}
	/** Находим и убираем служебные комментарии для Order By*/
	private static String uncommentOrderBy( String s ) {
//		return s;
		return s.replace( "/*ob#", "" )
				.replace( "#ob*/", "" )
				.replace( "~", "" );
	}

	final private Pattern inv_ptn = Pattern.compile("^(?:/\\*|--)\\s*inv:");

	private Map<String,String> strToMap( String str, String dlmtr )
	{
		if( S.isNullOrEmpty(str) )
			return Collections.emptyMap();

		Map<String,String> ret = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		String[] l = str.split( dlmtr );

		for( String s : l )
		{
			if( s.indexOf('=') == -1 )
				ret.put( s.trim(), null );
			else
			{
				String[] a = s.split("=");
				ret.put(a[0].trim(), a[1].trim());
			}
		}

		return ret;
	}

	private void postProcess()
	{
		final ListIterator< SQLItem > iter = itemList.listIterator();

		SQLItem prevItem = null;
		int numUnchIndex = -1;

		while( iter.hasNext() )
		{
			final SQLItem item = iter.next();

			if( item.getType() == SQLItem.ItemTypeEnum.COMMENT )
			{
				final String s = item.getValue().toString();
				final Matcher matcher = inv_ptn.matcher( s );
				if( matcher.find() )
				{
					iter.remove();

					String params = s.substring( matcher.end() );

					if( params.endsWith("*/") )
					{
						params = params.substring( 0, params.length() - 2 );
					}

					final Map<String,String> values = strToMap( params, ";" );

					if( "false".equalsIgnoreCase( values.get("replace_constants")) )
						this.replaceConstants = false;

					//System.out.println(	values );
				}
			}

			if( numUnchIndex == -1 && prevItem != null && U.in( item.getType(), INTEGER, DECIMAL ) )
			{
				if( prevItem.getType() == item.getType() )
					numUnchIndex = Math.max( iter.previousIndex()-1, 0 );
			}
			prevItem = item;
		}//end for

		if( numUnchIndex >= 0 )
		{
			for( int i = numUnchIndex; i < itemList.size(); i++ )
			{
				final SQLItem item = itemList.get(i);

				if( U.in( item.getType(), INTEGER, DECIMAL ) )
				{
					itemList.set( i, new SQLItem(item.getValue(), TEXT_EXPRESSION) );
				}
			}
		}
	}

	/** */
    private void checkAndParse() {
		if( !state ) {
            scaner.process( );
			postProcess();
            state = true;
        }
    }

	/** */
	@Override
	public Iterator<SQLItem> iterator() {
        checkAndParse();
        return itemList.iterator();
	}
	/** */
	@Override
	public ReaderScaner.ProcessResultEnum process( ReaderScaner.IContext context ) {

		do {

			if( currentState == null ) {

				if( context.current() == '?' ) {
					itemList.add( new SQLItem( null, SQLItem.ItemTypeEnum.INDEXED_PARAMETER ) );
					return ReaderScaner.ProcessResultEnum.OK;
				}
				else
                    if( context.current() == UnchangedState.DELIM )
                        currentState = AbstractState.getState( SQLItem.ItemTypeEnum.UNCHANGED );
                    else if( context.current() == ':' && context.next() != '=' ) //for PL/SQL
                        currentState = AbstractState.getState( SQLItem.ItemTypeEnum.NAMED_PARAMETER );
                    else if( ( context.current() == '/' && context.next() == '*') || ( context.current() == '-' && context.next() == '-') )
                            currentState = AbstractState.getState( SQLItem.ItemTypeEnum.COMMENT );
                    else if( context.current() == '"' )
                        currentState = AbstractState.getState( SQLItem.ItemTypeEnum.QUOTES );
                    else if( context.current() == '\'')
                         currentState = AbstractState.getState( STRING );
                    else
                            if( lang == SQL
								&&
								isDigit( context.current() )
							)
							{
								currentState = AbstractState.getState( INTEGER );
							}
//					else if( context.current() == '-' ) {
//						itemList.add( new SQLItem( "-", TEXT_EXPRESSION ) );
//						return ReaderScaner.ProcessResultEnum.OK;
//					}
                if( currentState == null )
                    currentState = AbstractState.getExpressionState( lang == SQL );
			}

			SQLItem item = currentState.process( context );

			if( item != null ) {

				currentState = null;

				if( item != AbstractState.EMPTY_ITEM )
					itemList.add(item);
			}

		} while( currentState == null );

		return ReaderScaner.ProcessResultEnum.OK;
	}
	/** */
	public String prepareActualSql( IParameters callbackParameters, List<Object> parameters )  {

        checkAndParse();

		StringBuilder sb = new StringBuilder( );

		Iterator<SQLItem> iter = iterator( );
		SQLItem	   currentItem = null;

		int nIndex = 0;

		while( iter.hasNext() ) {

			currentItem = iter.next();

			if( !replaceConstants && currentItem.isConstant() )
			{
				if( currentItem.getType() == STRING )
					sb.append("'").append(currentItem.getValue()).append("'");
				else
					sb.append( currentItem.getValue() );
				continue;
			}

			switch( currentItem.getType() ) {
				case INDEXED_PARAMETER:
					sb.append('?');
					if( callbackParameters != null )
						parameters.add( callbackParameters.getParameter(nIndex++) );
				break;
				case NAMED_PARAMETER:
					sb.append('?');
					if( callbackParameters != null )
						parameters.add( callbackParameters.getParameter( currentItem.getValue().toString() ) );
				break;
				case INTEGER:
				case DECIMAL:
				case STRING:
				{
					sb.append('?');
					parameters.add( currentItem.getValue() );
				}
				break;
				default :
					sb.append( currentItem.getValue() );
			}
		}//end while

		return uncommentOrderBy( sb.toString() );
	}
	/** */
	public String prepareActualSqlEx( final IParameters callbackParameters, List<Function> parameters ) {

        checkAndParse();

		StringBuilder sb = new StringBuilder( );

		Iterator<SQLItem> iter = iterator( );
		SQLItem	   currentItem = null;

		int nIndex = 0;

		while( iter.hasNext() ) {

			currentItem = iter.next();

			if( !replaceConstants && currentItem.isConstant() )
			{
				if( currentItem.getType() == STRING )
					sb.append("'").append(currentItem.getValue()).append("'");
				else
					sb.append( currentItem.getValue() );
				continue;
			}

			switch( currentItem.getType() ) {
				case INDEXED_PARAMETER:
					sb.append('?');
					if( callbackParameters != null )
					{
						final int idx = nIndex++;
						parameters.add( (Object o) -> callbackParameters.getParameter(idx) );
					}
				break;
				case NAMED_PARAMETER:
					sb.append('?');
					if( callbackParameters != null )
					{
						final String idx = currentItem.getValue().toString();
						parameters.add( (Object o) -> callbackParameters.getParameter( idx ));
					}
				break;
				case INTEGER:
				case DECIMAL:
				case STRING:
                    sb.append('?');
                    final Object val = currentItem.getValue();
                    parameters.add( (t) -> val);
				break;
				default :
					sb.append( currentItem.getValue() );
			}
		}//end while

		return uncommentOrderBy( sb.toString() );
	}

	/** */
	public String prepareActualSqlEx( List<Object> parameters ) {

		checkAndParse();

		StringBuilder sb = new StringBuilder( );

		Iterator<SQLItem> iter = iterator( );
		SQLItem	   currentItem = null;

		int nIndex = 0;

		while( iter.hasNext() ) {

			currentItem = iter.next();

			if( !replaceConstants && currentItem.isConstant() )
			{
				if( currentItem.getType() == STRING )
					sb.append("'").append(currentItem.getValue()).append("'");
				else
					sb.append( currentItem.getValue() );
				continue;
			}

			switch( currentItem.getType() ) {
				case INDEXED_PARAMETER:
					sb.append('?');
					final int idx = nIndex++;
					parameters.add( (Function<IParameters, Object>) (p) -> p.getParameter(idx));
					break;
				case NAMED_PARAMETER:
					sb.append('?');
					final String pn = currentItem.getValue().toString();
					parameters.add( (Function<IParameters, Object>) (p) -> p.getParameter(pn));
					break;
				case INTEGER:
				case DECIMAL:
				case STRING:
					sb.append('?');
					final Object val = currentItem.getValue();
					parameters.add( val);
					break;
				default :
					sb.append( currentItem.getValue() );
			}
		}//end while

		return uncommentOrderBy( sb.toString() );
	}


    static final private DateFormat to_dateFormatter  = new SimpleDateFormat("yyyy-MM-dd");
    static final private DateFormat to_dateFormatterM = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/** */
	public String prepareSqlWithParametersValue( IParameters callbackParameters ) {

        checkAndParse();

		StringBuilder sb = new StringBuilder( );

		Iterator<SQLItem> iter = iterator( );
		SQLItem	   currentItem = null;

		int nIndex = 0;

		while( iter.hasNext() ) {

			currentItem = iter.next();

			switch( currentItem.getType() ) {
				case INDEXED_PARAMETER:
					if( callbackParameters != null )
						sb.append( value2Str( callbackParameters.getParameter(nIndex++) ) );
                    else
    					sb.append('?');
				break;
				case NAMED_PARAMETER:
					if( callbackParameters != null )
						sb.append( value2Str( callbackParameters.getParameter( currentItem.getValue().toString() ) ) );
                    else
    					sb.append(':').append( currentItem.getValue() );
				break;
				case INTEGER:
				case DECIMAL:
					sb.append( currentItem.getValue() );
                break;
				case STRING:
					sb.append("'").append(currentItem.getValue()).append("'");
                break;
				default :
					sb.append( currentItem.getValue() );
}
		}//end while

		return uncommentOrderBy( sb.toString() );
	}

	/** */
//	private static String to_date( )
//	{
//		return LangEnum.getGLangPL() == LangEnum.PL_pgSQL ? "to_timestamp" : "to_date";
//	}

	/** */
    public static String value2Str( Object value ) {

        if( value == null )
            return " NULL ";

        Class clazz = value.getClass();

        if( clazz == String.class ) {
            String v = ((String)value).replace("'", "''");
            return " '" + v + "' ";
        }

		if( clazz == char[].class ) {
			String v = ( value.toString() ).replace("'", "''");
			return " '" + v + "' ";
		}

        if( clazz == Boolean.class ) {
            return ((Boolean)value) ? "1" : "0";
        }

        if( clazz == java.sql.Date.class ) {
            return " to_date('" + to_dateFormatter.format( (java.sql.Date) value ) + "','YYYY-MM-DD') ";
        }

        if( clazz == LocalDate.class ) {
//            return " trunc( " + to_date() + "( '" + to_dateFormatter.format( TypeConverter.convert( value, java.util.Date.class ) ) + "','YYYY-MM-DD')) ";
            return " to_date('" + to_dateFormatter.format( TypeConverter.convert( value, java.util.Date.class ) ) + "','YYYY-MM-DD') ";
        }

        if( value instanceof java.util.Date ) {
            return " to_timestamp( '" + to_dateFormatterM.format( (java.util.Date)value) + "','YYYY-MM-DD HH24:MI:SS') ";
//			return " " + to_date() + "( '" + to_dateFormatterM.format( (java.util.Date)value) + "','YYYY-MM-DD HH24:MI:SS') ";
        }

        if( value instanceof Temporal ) {
			return " to_timestamp( '" + to_dateFormatterM.format( TypeConverter.convert(value, java.util.Date.class ) ) + "','YYYY-MM-DD HH24:MI:SS') ";
        }

        return uncommentOrderBy( value.toString( ) );

    }

	/** */
	public String prepareExplainPlanSql( ) {

        checkAndParse();

		StringBuilder       sb = new StringBuilder( );

		Iterator<SQLItem> iter = iterator( );

		int nIndex = 1;

		while( iter.hasNext() ) {

			SQLItem	currentItem = iter.next( );

			if( !replaceConstants && currentItem.isConstant() )
			{
				if( currentItem.getType() == STRING )
					sb.append("'").append(currentItem.getValue()).append("'");
				else
					sb.append( currentItem.getValue() );
				continue;
			}

			switch( currentItem.getType() ) {
				case INDEXED_PARAMETER:
				case NAMED_PARAMETER:
				case INTEGER:
				case DECIMAL:
				case STRING:
					sb.append( ":p").append( nIndex++ ).append(' ');
                break;
				default :
					sb.append( currentItem.getValue() );
			}
		}//end while

		return uncommentOrderBy( sb.toString() );
	}

	/** */
	public String prepareSql4MetaData( ) {

        checkAndParse();

		StringBuilder       sb = new StringBuilder( );

		Iterator<SQLItem> iter = iterator( );

		while( iter.hasNext() ) {

			SQLItem	currentItem = iter.next( );

			switch( currentItem.getType() ) {
				case INDEXED_PARAMETER:
				case NAMED_PARAMETER:
				case INTEGER:
				case DECIMAL:
				case STRING:
					sb.append( "null ");
                break;
				default :
					sb.append( currentItem.getValue() );
			}
		}//end while

		return uncommentOrderBy( sb.toString() );
	}



    /** */
    public ExprInfo analyze( ) {
        return new SQLAnalyzerHelper( iterator() ).analyze();
    }

    /**
     * @return  */
//    public ExprInfo analyze1(  ) {
//
//        ExprInfo exprInfo = new ExprInfo();
//
//        List<String> words = this.itemList.stream( )
//                         .filter( (SQLItem i)->i.getType() == SQLItem.ItemTypeEnum.TEXT_EXPRESSION )
//                         .map   ( (i)->i.getValue().toString() )
//                         .flatMap ( (s)->
//                            Stream.of( s.split("\\s+") )
//                         )
//                         .filter ( S::isNotNullOrEmpty )
//                         .flatMap( (s)->
//                            //Stream.of( s.split(",") ).flatMap( (s1)->Stream.of(s1,",") )
//                            byComma(s).stream()
//                         )
//                         .map    ( String::toUpperCase )
//                         .collect( Collectors.toList() );
//
//        words.forEach(System.out::println);
//
//
//        exprInfo.setFromCount( (int)words.stream().filter( (s)->"FROM".equals(s) ).count() );
//
//        if( exprInfo.getFromCount() == 1 ) {
//
//            int i = words.indexOf("FROM");
//
//            for( ; i < words.size(); i++ )
//                if( words.get(i).equals("ORDER") && words.get(i+1).equals("BY") )
//                    break;
//        }
//        else
//            exprInfo.setMysterious( true );
//
//        return exprInfo;
//    }
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


                String testStr = "declare\n" +
                "  vsess VARCHAR2(30) := :sesid;\n" +
                "  vrep  NUMBER := :repid;\n" +
                "  vd1   DATE := :datbeg;\n" +
                "  vd2   DATE := :datend;\n" +
                "  vfil  NUMBER := :ifil;\n" +
                "  vsave NUMBER := :save;\n" +
                "  vshow NUMBER := to_number(:show);\n" +
                "  verr  VARCHAR2(2000);\n" +
                "begin\n" +
                "  DBMS_SESSION.set_nls('nls_numeric_characters','''. ''');\n" +
                "  DBMS_SESSION.set_nls('nls_date_format','''DD.MM.RRRR''');\n" +
                "  begin\n" +
                "    RG_Pkg.Cfg_Off;\n" +
                "    RG_Pkg.Calc_Report(pReportId =>vrep,\n" +
                "                       pDateFrom =>vd1,\n" +
                "                       pDateTo   =>vd2,\n" +
                "                       pBranch   =>vfil,\n" +
                "                       pSessionId=>vsess,\n" +
                "                       pSaveId   =>vsave,\n" +
                "                       pShowRule =>vshow);\n" +
                "    RG_Pkg.Show_Report(vrep,vsess);\n" +
                "    RG_PRN.Fill_Prn(vsess);\n" +
                "    RG_FX_TVIEW.load_palette(prepid => vrep);\n" +
                "    verr := 'OK';\n" +
                "    Commit;\n" +
                "  exception\n" +
                "    when others then\n" +
                "      verr := sqlerrm(sqlcode);\n" +
                "  end;\n" +
                "  :str := verr;\n" +
                "end;\n";
			String testStrAlt = "SELECT\n" +
					"SELECT cusH.ccusname||', '|| NVL(cP.Post_Name,l.reason)||'-'|| cusH.icusnum \"ФИО Должность\"\n" +
					"  FROM cus_lnk l\n" +
					" INNER JOIN cus cusH ON cusH.icusnum=l.id_cus_child AND l.id_cus_parent=:p4\n" +
					" LEFT JOIN cus_POST cP ON l.id_post=cP.Id_Post\n" +
					" WHERE l.id_lnk_type IN (1,21,22)\n" +
					"  AND (l.date_end IS NULL OR l.date_end >=SYSDATE)\n" +
					"ORDER     BY /**/ 1,\n" +
					"2/**/" +
					"\n" +
					"UNION\n" +
					"SELECT NVL(pcusattr.get_cli_atr(368, c.icusnum, SYSDATE, 0), c.ccusname)\n" +
					"||',-'|| c.icusnum \"ФИО Должность\"\n" +
					"  FROM cus c\n" +
					" WHERE c.iCUSnum = :p4 AND c.ccusflag=4\n" +
					"ORDER     BY 1,\n" +
                    "2"
					;
			//Тест на скобки
			String testStr3 = "SELECT\n /* inv: replace_Constants=false*/" +
					" qrslt .* from (select NAME,ID from (SELECT ID, cWhereName NAME\n" +
					" FROM FRM_FILTER\n" +
					" WHERE cFormName = :nana\n" +
					" AND (cBlockName = :nanana OR cBlockName IS NULL)\n" +
					" ORDER BY 1) qrslt " +
					"where upper(NAME||ID) like upper(:lov_parameter)) qrslt ";

			//Несколько ORDER BY
			String testFour = "SELECT IDSM_ENTRY FROM SM_ENTRY_MF\n" +
					"WHERE null is null\n" +
					"and idsm_prvd in ( 71, 74 ) and istate in ( 0, 1)  and istate <> 10 order by istate desc";
			//PL/SQL test

			/*
			SQLParser p = new SQLParser( testFive );

            List<Object> ps = new ArrayList<>();
            System.out.println( p.prepareActualSql(null, ps ) );

			ps.forEach(System.out::println);

			Logon.login(new String[]{"--ru.inversion.userid=FWFhJL0NBU1BFUkBkZXY4aQ=="});

			SQLDataSet<Object> ds = new SQLDataSet<>(new TaskContext(), Object[].class);
			ds.setSQL(testFour);
			ds.execute();

			 */

			SQLParser sp = new SQLParser ("/*inv:replace_constants=false*/ select 'Стандартное' FROM dual\n" +
					"union all\n" +
					"select 'Продленное' FROM dual\n" +
					"/*\n" +
					"http://jira.inversion.ru:8080/browse/XXIKERNEL-9087\n" +
					"*/");

			System.out.println("\u001B[35m" + "=>" + sp.prepareExplainPlanSql());

        } catch (Throwable ex) {
            //JInvErrorService.handleException(null, ex);
            ex.printStackTrace();
        }
    }



}
