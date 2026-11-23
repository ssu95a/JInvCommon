package ru.inversion.datacall;

import ru.inversion.dataset.parser.lexer.OracleSqlScanner;
import ru.inversion.dataset.parser.lexer.PostgresSqlScanner;
import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.util.List;
import java.util.function.Consumer;

import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.*;

/** Исходный SQL и разобранные токены для повторной подготовки вызова. */
public class CallDescriptor {

    private static final SqlScanner CALL_SCANNER = OracleSqlScanner.forCall();
    private static final SqlScanner PG_SCANNER = PostgresSqlScanner.forPlPgSql();

    private final List<SqlScanner.Token> tokenList;

    private final String sourceSql;
    private final LangEnum lang;

    private final boolean anonymousBlock;

    private CallDescriptor( String sourceSql, List<SqlScanner.Token> tokens, LangEnum lang )
    {
        this.sourceSql = sourceSql;
        this.tokenList = tokens;

        int first = nextExecutableIndex(0);

        anonymousBlock = startsAnonymousBlock(first);

        this.lang = lang == null || lang == LangEnum.UNKNOWN ? inferLanguage(first) : lang;
    }

    /** */
    private LangEnum inferLanguage( int first )
    {
        if( first == tokenList.size() )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "SQL text contains no executable tokens" );

        SqlScanner.Token token = tokenList.get(first);

        if( token.kind() == SYMBOL && sourceSql.charAt(token.start()) == '{' )
            return LangEnum.JDBC;

        return anonymousBlock ? LangEnum.PL_SQL : LangEnum.SQL;
    }

    /** */
    private boolean startsAnonymousBlock( int first )
    {
        int index = first;

        // Перед телом блока DECLARE/BEGIN могут стоять метки.
        while( isLabelDelimiter(index, "<<") )
        {
            int name = nextExecutableIndex(index + 1);

            if( name == tokenList.size() )
                return false;

            SqlScanner.Kind nameType = tokenList.get(name).kind();

            int end = nextExecutableIndex(name + 1);

            if( (nameType != WORD && nameType != QUOTED_IDENTIFIER) || !isLabelDelimiter(end, ">>") )
                return false;

            index = nextExecutableIndex(end + 1);
        }

        if( index == tokenList.size() )
            return false;

        SqlScanner.Token token = tokenList.get(index);

        return token.isWord( sourceSql, "DECLARE") || token.isWord( sourceSql, "BEGIN" );
    }

    /** */
    private boolean isLabelDelimiter( int index, String delimiter )
    {
        if( index == tokenList.size() )
            return false;

        SqlScanner.Token token = tokenList.get(index);

        return token.kind() == OPERATOR && token.end() - token.start() == delimiter.length() && sourceSql.startsWith(delimiter, token.start());
    }

    /** */
    private int nextExecutableIndex( int index )
    {
        while( index < tokenList.size() )
        {
            SqlScanner.Kind kind = tokenList.get(index).kind();

            if( kind != WHITESPACE && kind != COMMENT && kind != HINT && kind != PARAMETERIZE_OFF && kind != PARAMETERIZE_ON )
                break;

            index++;
        }

        return index;
    }

    /** */
    public static CallDescriptor create( String text ) {
        return create( text, LangEnum.UNKNOWN );
    }

    /** */
    public static CallDescriptor create( String text, LangEnum lang )
    {
        Checks.Require.text( text, "text" );

        SqlScanner scanner = lang == LangEnum.PL_pgSQL ? PG_SCANNER : CALL_SCANNER;

        return new CallDescriptor( text, scanner.scan(text), lang );
    }

    /** */
    String sourceSql() {
        return sourceSql;
    }

    /** */
    List<SqlScanner.Token> sqlTokens() {
        return tokenList;
    }

    /** Имена и позиции bind-параметров в порядке появления в SQL. */
    void forEachParameterId( Consumer<Comparable<?>> consumer )
    {
        int position = 0;

        for( SqlScanner.Token token : tokenList )
        {
            if( token.kind() == NAMED_PARAMETER )
                consumer.accept( sourceSql.substring(token.start() + 1, token.end()) );

            else if( token.kind() == POSITIONAL_PARAMETER )
                consumer.accept( position++ );
        }
    }

    /** */
    public boolean hasNamedParameter( String name ) {

        if( S.isNullOrEmpty(name) )
            throw new IllegalArgumentException( "'name' is null" );

        return tokenList.stream().anyMatch(t -> t.kind() == NAMED_PARAMETER
                && t.end() - t.start() == name.length() + 1
                && sourceSql.regionMatches(true, t.start() + 1, name, 0, name.length()));
    }
    /** */
    public boolean hasNamedParameters() {
        return tokenList.stream().anyMatch(t -> t.kind() == NAMED_PARAMETER);
    }
    /** */
    public boolean hasIndexedParameters() {
        return tokenList.stream().anyMatch(t -> t.kind() == POSITIONAL_PARAMETER);
    }

    /** */
    public LangEnum getLang() {
        return this.lang;
    }

    /** После комментариев и меток следует начало тела блока DECLARE или BEGIN. */
    public boolean isAnonymousBlock() { return anonymousBlock; }
}
