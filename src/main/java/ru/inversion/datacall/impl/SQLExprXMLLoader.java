package ru.inversion.datacall.impl;

import ru.inversion.datacall.*;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import javax.persistence.ParameterMode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.inversion.db.expr.SQLExpressionFactory.CUSTOM_FPZ_EXPR;
import static ru.inversion.parser.statement.LangEnum.*;

/**
 *
 * @author ssu
 */
public class SQLExprXMLLoader implements AutoCloseable {

    /** */
    static private Function<String, ParameterMode > pms = new Function< String, ParameterMode >() {
        @Override
        public ParameterMode apply( String name ) {
            if( !S.isNullOrEmpty(name) )
            {
                if( U.inChar( name.charAt(0), 'o', 'O' ) )
                    return ParameterMode.OUT;
                return ParameterMode.INOUT;
            }
            return ParameterMode.IN;
        }
    };

    final private URL location;
    /** */
    public SQLExprXMLLoader( URL location ) throws CallException {
        this.location = Objects.requireNonNull( location, "location is null" );
    }

    /** */
    private String loadScriptFromFile( String fileName ) throws CallException {

        try {

            URL scriptLocation = new URL( location, fileName );

            String expressionText = null;

            try( BufferedReader in = new BufferedReader( new InputStreamReader(scriptLocation.openStream(), StandardCharsets.UTF_8) ) ) {
                 expressionText = in.lines().collect( Collectors.joining("\n") );
            }

            return expressionText;
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error reading file '" + U.nvl( fileName, "<NULL>" ) + "' with PL/xxx script", th );
        }
    }

    /** */
    private void checkScriptText( String name, String scriptBody ) throws CallException {

        if( S.isNullOrEmpty(scriptBody) )
            throw new CallException( Tags.PRODUCT_LABEL + "For the expression '" + name + "' PL/xxx script is not defined.");
        //throw new CallException( "Для выражения '" + currentName + "' не задан скрипт.");

        if( scriptBody.length() < 10 )
            throw new CallException( Tags.PRODUCT_LABEL + "The length of the script '" + name + "' is too short. Maybe not well-formed <script> tag.",
                    "Check there is no character '\\n' after the right parenthesis of the <script> tag", null);
    }

    /** */
    private CallStatement prepareMetadata( String name, IDco dcoExpression, String scriptBody, List<ParameterMetadata> parametersList ) {

        try {

            // scriptBody += String.join( " ", "\n--:", location.toString(), dcoExpression.e("name").value() );

            final CallBuilder builder =
                CallBuilder.NEW()
                    .sql(scriptBody)
                    .removeAdditionalInfo( true )
                    .replaceConstants    ( TypeConverter.convert( dcoExpression.a("replaceConstants").getOrDefault( Boolean.FALSE ), Boolean.class ) )
                    .lang(dcoExpression.e("lang_enum").value() )
                    .autoCommit          ( TypeConverter.convert( dcoExpression.a("autoCommit").getOrDefault( Boolean.FALSE ), Boolean.class ) );

            if( parametersList != null )
                parametersList.forEach( builder::putParameter );
            else
                if( CUSTOM_FPZ_EXPR.equals( name) )
                    builder.parameterModeStrategy(pms);

            return builder.build();
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error while parsing expression '" +  name + "'", th );
        }
    }


    /** */
    public Map<String, CallStatement > parse( SqlDialect dialect ) throws CallException {

        if( dialect == null )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'dialect' is null" );

        String currentName = null;
        final String[] currentLang = new String[1];

        final Map<String, CallStatement> expressionList = new HashMap<>();

        Function<IDco,LangEnum> get_Lang = new Function< IDco, LangEnum >() {
            @Override
            public LangEnum apply( IDco dco ) {

                if( dco.hasAttribute("lang") )
                {
                    currentLang[0] = dco.a("lang").get().toString();
                    LangEnum lang = LangEnum.of( currentLang[0] );

                    if( lang == null || lang == UNKNOWN )
                        throw new IllegalStateException( Tags.PRODUCT_LABEL +  "bad value lang for '" + currentLang[0] + '"' );

                    dco.e("lang_enum").set( lang );

                    return lang;
                }
                return UNKNOWN;
            }
        };

        try {

            final LangEnum targetLang  = dialect.plLang();

            IDco dco = Dco.parseXml( location );

            IDco dcoExprList = null;

            if( "sql_expr_list".equals( dco.getName() ) )
            {
                dcoExprList = dco;
            }
            else
            {
                for( IDco d : dco.select("//sql_expr_list") ) {
                     dcoExprList = d;
                     break;
                }
            }

            if( dcoExprList == null )
                throw new IllegalStateException("no 'sql_expr_list' in xml");

            // SQL Выражения
            for( IDco dcoExpression : dcoExprList.select("expression") )
            {
                currentName = dcoExpression.a("name").value();

                if( S.isNullOrEmpty(currentName) )
                    throw new CallException( Tags.PRODUCT_LABEL + "The XML node <expression> without 'name' attribute for PL/xxx expression" );

                LangEnum lang = get_Lang.apply(dcoExpression);

                if( lang.isSpec() && lang != targetLang  )
                    continue;

                // Параметры
                final List<ParameterMetadata> parametersList = new ArrayList<>();
                ParameterMetadataBuilder pmb = new ParameterMetadataBuilder();

                for( IDco dcoParameter : dcoExpression.select("parameters/parameter") )
                {
                    for( IDco a : dcoParameter.attributes() )
                    {
                        switch( a.getName() ) {
                            case "name":
                                pmb.name( a.value() );
                                break;
                            case "position":
                                pmb.position(a.value());
                                break;
                            case "mode":
                                pmb.mode((String)a.value());
                                break;
                            case "sqlType":
                                pmb.sqlType(a.value());
                                break;
                            case "javaType":
                                pmb.javaType((String)a.value());
                                break;
                            case "defaultValue":
                                if( !S.isNullOrEmpty((String)a.get()) )
                                     pmb.defaultValue(a.value());
                                break;
                            case "elementType":
                                pmb.elementType(a.value());
                                break;
                            case "logValue":
                                pmb.logValue(a.value());
                                break;
                            case "return":
                                pmb.isReturnValue( a.value() );
                                break;
                            default:
                                throw new IllegalStateException ( Tags.PRODUCT_LABEL + "Неизвестный атрибут '" +  a.getName() + "' в описании параметра выражения '" + currentName );
                        }//end switch
                    }//end for

                    parametersList.add( pmb.build() );

                }//end for


                final List<IDco> dcoScriptList = new ArrayList<>();

                // Скрипты
                for( IDco d : dcoExpression.select("script") )
                {
                    lang = get_Lang.apply( d );

                    if( lang.isSpec() && lang != targetLang  )
                        continue;

                    dcoScriptList.add( d );

                }//end for

                // если скриптов не нашли, то ищем файлы со скриптами
                if( dcoScriptList.isEmpty() )
                {
                    // если нет файла, то нет и скрипта
                    if( !dcoExpression.hasAttribute("file") )
                         continue;
                }

                IDco dcoScript = null;

                // если скриптов несколько
                if( dcoScriptList.size() > 1 )
                {
                    for( IDco d : dcoScriptList )
                    {
                        dcoScript = d;
                        if( d.hasItem("lang_enum") && ((LangEnum)d.e("lang_enum").value()).isSpec() )
                            break;
                    }//end for
                }//end if
                else if( dcoScriptList.size() == 1 )
                    dcoScript = dcoScriptList.get(0);

                String scriptBody = dcoScript == null ? null : dcoScript.value();

                if( S.isNullOrEmpty(scriptBody) )
                {
                    String fileName = dcoExpression.a("file").value();

                    if( S.isNullOrEmpty(fileName) )
                        throw new CallException( Tags.PRODUCT_LABEL + "For the expression '" + currentName + "' PL/xxx script is not defined.",
                                "Missing or empty xml <script> tag or attribute <file>", null );

                    scriptBody = loadScriptFromFile( fileName );
                }

                checkScriptText( currentName, scriptBody );

                if( dcoScript != null && dcoScript.hasItem("lang_enum") )
                    dcoExpression.e("lang_enum").set( dcoScript.e("lang_enum").get() );
                else
                {
                    if(!dcoExpression.hasItem("lang_enum") )
                        dcoExpression.e("lang_enum").set( targetLang );
                }

                final CallStatement cs = expressionList.get(currentName);
                if( cs == null )
                {
                    expressionList.put( currentName, prepareMetadata( currentName, dcoExpression, scriptBody, parametersList) );
                }
                else {

                    final LangEnum cLang = dcoExpression.e("lang_enum").value();

                    // контроль дублирования
                    if( cLang.equals( cs.getLang() ) )
                        throw new IllegalStateException( Tags.PRODUCT_LABEL + "Дублирование SQL выражения '" + currentName + "' с одинаковым lang '" + cLang + "' в переданном XML файле");

                    //SQL Выражение сохраняется с учетом приоритета, по языкам
                    //Специализированный язык имеет приоритет над обобщенными - JDBC,SQL,PL
                    if( !cs.getLang().isSpec() )
                        expressionList.put( currentName, prepareMetadata( currentName, dcoExpression, scriptBody, parametersList ) );
                }

            }//end for expression

            return expressionList;
        }
        catch( Throwable th ) {
            throw new CallException (
                    Tags.PRODUCT_LABEL + "Error when parsing XML expression at " + location
                            + ", expression '" + U.nvl( currentName, "<undef>") + "', lang '" + U.nvl( currentLang[0], "<undef>"), th
            );
        }
    }

    /** */
    @Override
    public void close() {
    }
}
