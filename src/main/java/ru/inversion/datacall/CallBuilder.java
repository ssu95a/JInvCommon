package ru.inversion.datacall;

import ru.inversion.parser.statement.LangEnum;
import ru.inversion.parser.token.Token;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.ParameterMode;
import java.sql.Types;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static javax.persistence.ParameterMode.OUT;
import static ru.inversion.parser.token.Token.TypeEnum.INDEXED_PARAMETER;
import static ru.inversion.parser.token.Token.TypeEnum.NAMED_PARAMETER;
import static ru.inversion.utils.ParametersValues.ParamIdComparator;


/** */
public class CallBuilder {

    /** */
    private String sql;

    /** */
    private LangEnum lang = LangEnum.UNKNOWN;

    /** */
    private Map<Comparable<?>, ParameterMetadata<?>> parametersMetaData = null;

    /** */
    private final List<Comparable<?>> outParameterOrder = new ArrayList<>();

    /** */
    private boolean removeAdditionalInfo = true;

    /** */
    private boolean replaceConstants = false;

    /** */
    private boolean autoCommit = false;

    /** Назначение типа параметров, в зависимости от названия */
    private Function<String, ParameterMode> parameterModeStrategy = (s)->ParameterMode.IN;

    private CallBuilder() {
    }

    public static CallBuilder NEW() {
        return new CallBuilder();
    }

    /** */
    public CallBuilder sql( String s ) {
        sql = s;
        return this;
    }

    /** */
    public CallBuilder lang( LangEnum l ) {
        lang = l;
        return this;
    }

    /** */
    public CallBuilder removeAdditionalInfo( boolean v ) {
        removeAdditionalInfo = v;
        return this;
    }

    /** */
    public CallBuilder replaceConstants( boolean v ) {
        replaceConstants = v;
        return this;
    }

    /** */
    public CallBuilder autoCommit( boolean v ) {
        autoCommit = v;
        return this;
    }

    /** Присваивание типа параметра в зав-ти от названия */
    public CallBuilder parameterModeStrategy( Function<String, ParameterMode> v ) {
        parameterModeStrategy = v;
        return this;
    }

    /** */
    private Map<Comparable<?>, ParameterMetadata<?>> parameters( ) {
        if( parametersMetaData == null )
            parametersMetaData = new TreeMap<>( ParamIdComparator );
        return parametersMetaData;
    }


    /** */
    public CallBuilder putParameter( ParameterMetadata pm ) {
        final Comparable<?> key = pm.isIndexed() ? pm.getPosition() : pm.getName();
        parameters().put( key, pm );
        if( pm.getMode() != ParameterMode.IN )
            outParameterOrder.add(key);
        return this;
    }

    /** */
    private CallBuilder parameter(
        ParameterMode mode,
        int position,
        String name,
        Integer sqlType,
        String elementType,
        Class   javaType,
        Object  defaultValue,
        boolean logValue
    )
        throws CallException
    {
        final ParameterMetadataBuilder pmb = new ParameterMetadataBuilder();
        if( position == -1 )
            pmb.name(name);
        else
            pmb.position(position);

        pmb.mode(mode).javaType(javaType).sqlType(sqlType).elementType(elementType).defaultValue(defaultValue).logValue(logValue?"1":"0");

        return putParameter( pmb.build() );
    }

    /** */
    public CallBuilder parameter( ParameterMode mode, String name, Integer sqlType, String elementType, Class javaType, Object defaultValue, boolean logValue ) throws CallException {
        return parameter( mode, -1,  name,  sqlType, elementType, javaType, defaultValue, logValue );
    }

    /** */
    public CallBuilder parameter( ParameterMode mode, int position, Integer sqlType, String elementType, Class javaType, Object defaultValue, boolean logValue ) throws CallException {
        return parameter( mode, position,  null,  sqlType, elementType, javaType, defaultValue, logValue );
    }

    /** */
    public CallStatement build( ) throws CallException {

        try {

            if( S.isNullOrEmpty(sql) )
                throw new IllegalStateException( "'sql' is null or empty");

            CallDescriptor descriptor = CallDescriptor.create( sql, lang );

            final Predicate<Token<?>> filter = ( token) -> U.in( token.getType(), NAMED_PARAMETER, INDEXED_PARAMETER );

            //инициализация параметров
            int index = 0;

            for( Token t : U.iterable( descriptor.getTokenIterator(filter) ) ) {

                if( t.getType() == NAMED_PARAMETER ) {

                    String name = (String)t.getValue();

                    ParameterMetadata p = parameters().get( name );

                    if( p == null )
                    {
                        final ParameterMode mode = parameterModeStrategy.apply(name);

                        p = new RawParameterMetadata( name,
                                                   mode == OUT ? Types.VARCHAR : null,
                                                   null, mode, null, false, null, true, false );
                        putParameter( p );
                    }
                }
                else if( t.getType() == INDEXED_PARAMETER ) {

                    ParameterMetadata p = parameters().get( index );

                    if( p == null )
                    {
                        p = new RawParameterMetadata( index, null, null, ParameterMode.IN, null, false, null, true, false );
                        putParameter( p );
                    }

                    index++;
                }
            }

            if( descriptor.getLang() == LangEnum.JDBC )
                removeAdditionalInfo = true;

            return new CallStatement (
                descriptor, parametersMetaData, outParameterOrder, removeAdditionalInfo, replaceConstants, autoCommit
            );
        }
        catch( Throwable th ) {
            throw new CallException(Tags.PRODUCT_LABEL + "Error on build CallStatement", th );
        }
    }

//    public <T> T getProperty(String propertyName) {
//        return properties == null ? null : (T)properties.get(propertyName);
//    }
//
//    public void setProperty(String propertyName, Object value) {
//        if( properties == null )
//            properties = new HashMap<>();
//        properties.put( propertyName, value );
//    }

    /** */
    public static void main(String[] args) {

        try {

//            StoredStatement statement = null;
//            StoredStatement statement = new StoredStatement(
//                Files.readAllLines (
//                    new File("D:\\JavaProjects\\XXI\\FX\\FXPdoc\\src\\main\\resources\\ru\\inversion\\fxpdoc\\nborder\\plsql\\fillFromDpDoc.sql").toPath()
//                ).stream ( )
//                 .collect(
//                    Collectors.joining("\n")
//                 ).replace("ORDER BY cName","ORDER BY 1")
//            );


            final  String plSQLStruct = "DECLARE\n" +
                    "   TYPE Rec IS RECORD (\n" +
                    "        ID INTEGER,\n" +
                    "        NAME VARCHAR2(100)\n" +
                    "   ); \n" +
                    "   l_rec Rec;  \n" +
                    "BEGIN\n" +
                    "   l_rec.id   := 50; \n" +
                    "   l_rec.name := 'test name';\n" +
                    "   DBMS_OUTPUT.PUT_LINE('id = ' || l_rec.id || ', name = ' || l_rec.name );\n" +
                    "   :rec := l_rec" +
                    "END;";

            CallStatement st =

                    new CallBuilder()
                            .sql(plSQLStruct)
                                .parameter(OUT, "rec", Types.STRUCT, "Rec", null, null, true )
                            .build();


            st.createCallWork(null,null);

        } catch (Throwable th ) {
            th.printStackTrace();
        }
    }

}
