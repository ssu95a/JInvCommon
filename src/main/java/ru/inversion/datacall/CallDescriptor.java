package ru.inversion.datacall;

import ru.inversion.parser.statement.LangEnum;
import ru.inversion.parser.token.Token;
import ru.inversion.parser.token.TokenHandler;
import ru.inversion.utils.*;

import java.sql.Types;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static ru.inversion.parser.token.Token.TypeEnum.*;

public class CallDescriptor {

    /** */
    final private LinkedList<Token<?>> tokenList;

    /** */
    final private LangEnum lang;

    /** */
    final private boolean isAnonymousBlock;

    /** */
    private CallDescriptor( LinkedList<Token<?>> tokenList, LangEnum l ) {

        this.tokenList = tokenList;

        if( l == null || l == LangEnum.UNKNOWN )
            lang = determinateLang( tokenList );
        else
            lang = l;

        Predicate<Token<?>> f = new Predicate< Token<?> >() {
            @Override
            public boolean test( Token t ) {
                return
                    t.getType() == EXPRESSION
                    &&
                    (
                        "DECLARE".equalsIgnoreCase( (String)t.getValue() ) || "BEGIN".equalsIgnoreCase( (String)t.getValue()
                    )
                );
            }
        };

        isAnonymousBlock = tokenList.stream().anyMatch(f);
    }
    /** */
    private static LangEnum determinateLang( List<Token<?>> tokenList ) {

        int i = 0;

        // skip first comment
        for(; i < tokenList.size()
                && U.in( tokenList.get(i).getType(), COMMENT, NL );
                    i++ )
            ;

        Token<?> token = tokenList.get(i);

        if( token.getType() == Token.TypeEnum.LEFT_BRACE )
            return LangEnum.JDBC;

        if( token.getType() == EXPRESSION
            && (
                "DECLARE".equalsIgnoreCase( (String)token.getValue() )
                ||
                "BEGIN".equalsIgnoreCase( (String)token.getValue() )
            )
        ) {
            return LangEnum.PL_SQL;
        }
        return LangEnum.SQL;
    }

    /** */
    public static CallDescriptor create( String text ) {
        return create( text, LangEnum.UNKNOWN );
    }

    /** */
    public static CallDescriptor create( String text, LangEnum lang )
    {
        final LinkedList<Token<?>> l =
            StreamSupport.stream( U.iterable( new TokenHandler( text ).iterator() ).spliterator(), false )
                .collect( Collectors.toCollection(LinkedList::new) );

        return new CallDescriptor( l, lang );
    }

    /** */
    public Iterator<Token<?>> getTokenIterator( Predicate<Token<?>> predicate ) {
        return U.mapIter( tokenList.iterator(), Function.identity(), predicate );
    }
    /** */
    public void preparePLSql( CallPrepareContext context ) {
        tokenList.forEach( t->context.sqlMaker().accept( t, context ));
    }
    /** */
    public boolean hasNamedParameter( String name ) {

        if( S.isNullOrEmpty(name) )
            throw new IllegalArgumentException( "'name' is null" );

        return tokenList.stream().anyMatch( (t)->t.getType() == Token.TypeEnum.NAMED_PARAMETER && name.equalsIgnoreCase( (String)t.getValue() ) );
    }
    /** */
    public boolean hasNamedParameters() {
        return tokenList.stream().anyMatch( (t)->t.getType() == Token.TypeEnum.NAMED_PARAMETER );
    }
    /** */
    public boolean hasIndexedParameters() {
        return tokenList.stream().anyMatch( (t)->t.getType() == Token.TypeEnum.INDEXED_PARAMETER );
    }

    /** */
    public LangEnum getLang() {
        return this.lang;
    }

    /** */
    public boolean isAnonymousBlock() { return isAnonymousBlock; }
}
