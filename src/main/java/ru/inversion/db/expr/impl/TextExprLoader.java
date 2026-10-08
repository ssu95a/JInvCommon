package ru.inversion.db.expr.impl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.persistence.ParameterMode;
import ru.inversion.db.expr.SQLExpressionException;
import static ru.inversion.db.expr.impl.SQLExprXMLLoader.parseExpressionText;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

/** */
public class TextExprLoader implements AutoCloseable {

    final private String textExpr;

    public TextExprLoader( String textExpr ) {

        if(S.isNullOrEmpty(textExpr) )
            throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "'textExpr' is null or empty" );

        this.textExpr = textExpr;
    }
/*
    public ParameterMetadata( String name,
                              Integer sqlType,
                              Class javaType,
                              ParameterMode mode, Object defaultValue, boolean isDefInParameterList, String elementType )
        */

    /** */
    private ParameterMetadata createParameter( String name ) {

        boolean isOut = name.matches("^[oO]\\d*$");

        ParameterMetadata pm = new ParameterMetadata(name, Types.VARCHAR, String.class,
                isOut ? ParameterMode.OUT : ParameterMode.INOUT,
                null, true, null, true
        );

        return pm;
    }

    /** */
    public ExpressionMetadata parse( ) throws SQLExpressionException {

        List<Pair<String,Integer> > parametersList = new ArrayList<>();

        String currentScript = parseExpressionText( textExpr, parametersList, null );

        if( (int)parametersList.stream().filter( (p)->p.first == null ).count() > 0 )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "Only named parameters supported! In the expression was found indexed parameters." );

        final Map<String, ParameterMetadata> metaParameterNamedMap = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );

        int parameterIndex = 0;

        for( String s : U.iterable( parametersList.stream().map( (p)->p.first ).iterator() ) ) {

            ParameterMetadata pm = metaParameterNamedMap.get(s);

            if( pm == null ) {
                pm = createParameter(s);
                metaParameterNamedMap.put( s, pm );
            }

            pm.addJDBCStatementIndex( parameterIndex );

            parameterIndex++;
        }

        // parametersList.forEach( p->metaParameterNamedMap.put( p.getFirst(), createParameter( p.getFirst() ) ) );

        return new ExpressionMetadata( null, currentScript, metaParameterNamedMap, null );
    }

    @Override
    public void close() {

    }
}
