package ru.inversion.db.expr.impl;

import ru.inversion.dataset.parser.SQLItem;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.db.expr.SQLExpressionException;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.ParameterMode;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

//import ru.inversion.dataset.parser.SQLItem;
//import ru.inversion.dataset.parser.SQLParser;
//import static ru.inversion.dataset.parser.SQLParser.value2Str;

/**
 *
 * @author ssu
 */
public class SQLExprXMLLoader implements AutoCloseable {

    static final private XMLInputFactory factory = XMLInputFactory.newInstance();

    final private URL location;
    final private List<ExpressionMetadata> expressionList = new ArrayList<>();

    private String currentName,
                   currentLang,
                   currentNote,
                   currentFile,
                   currentScript;

    private List<ParameterMetadata> currentParametersList = null;

    final private XMLEventReader xmlReader;

    /** */
    public SQLExprXMLLoader(URL location) throws SQLExpressionException {

        Objects.requireNonNull( location, "location is null" );

        this.location = location;

        try {
            xmlReader = factory.createXMLEventReader( location.openStream() );
        } catch (Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error opening XML with information about PL/SQL expressions. location - " + location.toString(), th );
        }
    }

    /** */
    public List<ExpressionMetadata> parse( ) throws SQLExpressionException {

        try {

            // выход на начало документа
            while( xmlReader.hasNext() ) {

                XMLEvent event = xmlReader.nextEvent();

                if( event.isStartElement() ) {
                    if( event.asStartElement().getName().getLocalPart().equals("sql_expr_list") )
                        break;
                }
            }

            while( xmlReader.hasNext() ) {

                XMLEvent event = xmlReader.nextEvent();

                //выход из разбора, по достижению конца документа
                if( event.isEndElement() ) {

                    String name = event.asEndElement().getName().getLocalPart();

                    if( name.equals("sql_expr_list") )
                        break;

                    if( name.equals("expression") ) {

                        expressionList.add( prepareMetadata() );

                        currentName  = null;
                        currentLang  = null;
                        currentNote  = null;
                        currentFile  = null;
                        currentScript= null;
                        currentParametersList=null;
                    }
                }

                if( event.isStartElement() ) {

                    StartElement se = event.asStartElement();

                    if( se.getName().getLocalPart().equals("expression") ) {

                        Iterator iterator = se.getAttributes();

                        while( iterator.hasNext() )
                        {
                            Attribute attribute = (Attribute) iterator.next();

                            switch( attribute.getName().getLocalPart() ) {
                                case "name":
                                    currentName = attribute.getValue();
                                break;
                                case "lang":
                                    currentLang = attribute.getValue();
                                break;
                                case "file":
                                    currentFile = attribute.getValue();
                                break;
                                case "note":
                                    currentNote = attribute.getValue();
                                break;
                            }//end switch
                        }//end while
                    }

                    if( se.getName().getLocalPart().equals("script") ) {
                        event = xmlReader.nextEvent();
                        currentScript = event.asCharacters().getData();
                    }

                    if( se.getName().getLocalPart().equals("parameters") ) {
                        currentParametersList = new ArrayList<>();
                        continue;
                    }

                    if( se.getName().getLocalPart().equals("parameter") && currentParametersList != null ) {

                        Iterator iterator = se.getAttributes();

                        ParameterMetadataBuilder pmb = new ParameterMetadataBuilder();

                        while( iterator.hasNext() )
                        {
                            Attribute attribute = (Attribute) iterator.next();

                            switch( attribute.getName().getLocalPart() ) {
                                case "name":
                                    pmb.name(attribute.getValue());
                                break;
                                case "position":
                                    pmb.position(attribute.getValue());
                                break;
                                case "mode":
                                    pmb.mode(attribute.getValue());
                                break;
                                case "sqlType":
                                    pmb.sqlType(attribute.getValue());
                                break;
                                case "javaType":
                                    pmb.javaType(attribute.getValue());
                                break;
                                case "defaultValue":
                                    pmb.defaultValue(attribute.getValue());
                                break;
                                case "elementType":
                                    pmb.elementType(attribute.getValue());
                                break;
                                case "logValue":
                                    pmb.logValue(attribute.getValue());
                                break;
                            }//end switch
                        }//end while
                        currentParametersList.add( pmb.build() );
                    }//end if
                }
            }
            return expressionList;
        }
        catch( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error when parsing XML with information about the attributes", th );
        }
    }

    /** */
    private String loadScriptFromFile( ) throws SQLExpressionException {

        try {

            URL scriptLocation = new URL( location, currentFile );

            String expressionText = null;

            try( BufferedReader in = new BufferedReader( new InputStreamReader(scriptLocation.openStream(), "UTF-8") ) ) {
                expressionText = in.lines().collect( Collectors.joining("\n") );
            }

            return expressionText;
        }
        catch( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error reading file '" + U.nvl( currentFile, "NULL" ) + "' with SQL script", th );
        }
    }

    /** */
    static public String parseExpressionText( String expressionString, List<Pair<String,Integer>> parametersList, String additionalInfo ) throws SQLExpressionException {

        try {

//            StringBuilder sb = new StringBuilder();

            SQLParser parser = new SQLParser( expressionString, SQLParser.Lang.PL_SQL );

            Iterator< SQLItem > iterator = parser.iterator();

            int nIndexedParameters = 0;
            int nCounter = 1;

            StringBuilder sb = new StringBuilder();

            while( iterator.hasNext() ) {
                SQLItem item = iterator.next();
                switch( item.getType() ) {
                    case NAMED_PARAMETER:
                        parametersList.add( Pair.makePair( (String)item.getValue(), null ) );
                        sb.append( '?' ).append(" /* ").append(nCounter++).append(", :").append(item.getValue()).append(" */");
                        break;
                    case INDEXED_PARAMETER:
                        parametersList.add( Pair.makePair( null, nIndexedParameters++ ) );
                        sb.append( '?' ).append("/*").append(nCounter++).append("*/");
                        break;
                    case STRING:
                        //sb.append("'").append( item.getValue() ).append("'");
                        sb.append( value2Str(item.getValue()) );
                        break;
                    default:
                        sb.append( item.getValue() );
                }
            }

//            PLSqlStatement plSQL = new PLSqlStatement(expressionString);
//            plSQL.prepareSql(new AbstractPLSqlMakerBase() {
//                 int nIndexedParameters = 0;
//                 int nCounter = 1;
//                @Override
//                public void accept( Token token, PLSqlPrepareContext plSqlPrepareContext ) {
//                    switch( token.getType() ) {
//                        case NAMED_PARAMETER:
//                            parametersList.add( Pair.makePair( (String)token.getValue(), null ) );
//                            sb.append( '?' ).append(" /* ").append(nCounter++).append(", :").append(token.getValue()).append(" */");
//                        break;
//                        case INDEXED_PARAMETER:
//                            parametersList.add( Pair.makePair( null, nIndexedParameters++ ) );
//                            sb.append( '?' ).append("/*").append(nCounter++).append("*/");
//                        break;
//                    default:
//                        token.accept(sb);
//                    }
//                }
//            }, null, null
//            );

            if( S.isNotNullOrEmpty( additionalInfo ) )
                sb.append('\n').append( "--" ).append( additionalInfo );

            return sb.toString();
        }
        catch( Throwable ex ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error parsing PL/SQL statement", ex );
        }
    }

    /** */
    private String parseExpressionText( String expression, List<Pair<String,Integer>> parametersList ) throws SQLExpressionException {
        return parseExpressionText( expression, parametersList, String.join( ": ", location.toString(), currentName ) );
    }

    /** */
    private void checkScriptText( ) throws SQLExpressionException {

        if( S.isNullOrEmpty(currentScript) )
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "For the expression '" + currentName + "' PL/SQL script is not defined.");
            //throw new SQLExpressionException( "Для выражения '" + currentName + "' не задан скрипт.");

        if( currentScript.length() < 10 )
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "The length of the script '" + currentName + "' is too short. Maybe not well-formed <script> tag.",
                                              "Check there is no character '\\n' after the right parenthesis of the <script> tag", null);
    }

    /** */
    private ExpressionMetadata prepareMetadata(  ) throws SQLExpressionException {

        if( S.isNullOrEmpty(currentName)  )
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "The XML node <expression> without 'name' attribute for PL/SQL expression" );

        if( S.isNullOrEmpty(currentScript) && S.isNullOrEmpty(currentFile) )
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "For the expression '" + currentName + "' PL/SQL script is not defined.",
                                              "Missing or empty xml <script> tag or attribute <file>", null );

        try {

        if( currentScript == null )
            currentScript = loadScriptFromFile( );

        checkScriptText( );

        List<Pair<String,Integer>> parametersList = new ArrayList<>();

        currentScript = parseExpressionText( currentScript, parametersList );

        int nPositionParameter = (int)parametersList.stream().filter( (p)->{return p.first == null;} ).count();

        final List<ParameterMetadata> metaParameterIndexList = new ArrayList<>( nPositionParameter );

        if( nPositionParameter > 0 )
        {
            for( int i = 0; i < nPositionParameter; i++ )
                metaParameterIndexList.add(null);
        }

        final Map<String, ParameterMetadata> metaParameterNamedMap = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );

        if( currentParametersList != null )
        {
            currentParametersList.forEach (
                (p)-> {
                    if( p.isIndexed() )
                        metaParameterIndexList.set( p.getPosition(), p );
                    else
                        metaParameterNamedMap.put( p.getName(), p);
                }
            );
        }

        ParameterMetadata pm;

        for( int i = 0; i < parametersList.size() ; i++ ) {

            Pair<String,Integer> p = parametersList.get( i );

            if( p.first != null ) {

                pm = metaParameterNamedMap.get( p.first );

                if( pm == null ) {
                    pm = new ParameterMetadata( p.first, null, null, ParameterMode.IN, null, false, null, true );
                    metaParameterNamedMap.put ( p.first, pm );
                }

                pm.addJDBCStatementIndex(i);
            }
            else {

                pm = metaParameterIndexList.get( p.second );

                if( pm == null ) {
                    pm = new ParameterMetadata( p.second, null, null, ParameterMode.IN, null, false, null, true );
                    metaParameterIndexList.set( p.second, pm );
                }

                pm.addJDBCStatementIndex(i);
            }

        }//end for

        return new ExpressionMetadata( currentName, currentScript, metaParameterNamedMap, metaParameterIndexList );

        }
        catch( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error while parsing expression '" + currentName + "'", th );

        }
    }

    /** */
    @Override
    public void close() throws Exception {
    }
}
