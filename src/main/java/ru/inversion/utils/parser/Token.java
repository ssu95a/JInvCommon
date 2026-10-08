package ru.inversion.utils.parser;

import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.io.Writer;
import java.util.function.Consumer;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

/**
 *
 * @author ssu
 */
public class Token<T> implements Consumer<Appendable> {
    /** */
    public enum TypeEnum {

        UNKNOWN,

        EXPRESSION,

        INDEXED_PARAMETER,

        NAMED_PARAMETER,

        STRING,

        COMMENT,

		INTEGER,

        INTEGER_STRING, // число в строковом представлении

		DECIMAL,

        TEXT_IN_QUOTES,

        ARITHMETIC_OPERATOR,

        COMPARISON_OPERATOR,

        STRINGS_OPERATOR,

        PARENTHESIS,

        COMMA,     // запятая

        SEMICOLON, // точка с запятой

        DOT,       // точка

        DOUBLE_DOT,// двойная точка

        NL,        // new line

        TAB,       // tab

        NAMED_NOTATION, //=>

        ASSIGNMENT_PL, // :=

        BRACE, // {

        PG_CAST_OPERATOR, // ::

        PG_POSITIONAL_PARAMETER,

        UNCHANGED_MARK,

        COLON,

        SHARP,

        // HIGH LEVEL

        // SQL
        ORDER_BY,

        GROUP_BY,

        ADDITIONAL_PREDICATE,

        MARK_COLUMN,

        QUERY_HINT,

        SELECT,

        WHERE,

        HAVING,

        LIMIT, OFFSET, FETCH, TOP,
        UNION, INTERSECT, EXCEPT,

        // FRU
        FRU_PARAMETER,

        FRU_SECTION_HEADER,

        BIL,

        FRU_LINE
    }
    final public static Token<String> EmptyComment        = new Token<>( TypeEnum.COMMENT, S.EMPTY_STRING );
    final public static Token<Character> Comma            = new Token<>( TypeEnum.COMMA, ','           );
    final public static Token<Character> Dot              = new Token<>( TypeEnum.DOT,   '.'           );
    final public static Token<Character> Semicolon        = new Token<>( TypeEnum.SEMICOLON,';'        );
    final public static Token<Character> Nl               = new Token<>( TypeEnum.NL,    '\n'          );
    final public static Token<String> Nls                 = new Token<>( TypeEnum.NL,    "\n"          );
    final public static Token<Character> Tab              = new Token<>( TypeEnum.TAB,   '\t'          );
    final public static Token<String> NamedNotation       = new Token<>( TypeEnum.NAMED_NOTATION, "=>" );
    final public static Token<String> Assignment          = new Token<>( TypeEnum.ASSIGNMENT_PL, ":="  );
    final public static Token<Character> ParenthesisLeft  = new Token<>( TypeEnum.PARENTHESIS,   '('   );
    final public static Token<Character> ParenthesisRight = new Token<>( TypeEnum.PARENTHESIS,   ')'   );
    final public static Token<Character> LeftBrace        = new Token<>( TypeEnum.BRACE,         '{'   );
    final public static Token<Character> RightBrace       = new Token<>( TypeEnum.BRACE,          '}'  );
    final public static Token<Character> IndexedParameter = new Token<>( TypeEnum.INDEXED_PARAMETER,'?');
    final public static Token<Character> UnchangedMark    = new Token<>( TypeEnum.UNCHANGED_MARK,'~'   );
    final public static Token<Character> Sharp            = new Token<>( TypeEnum.SHARP, '#'           );
    final public static Token<String> PgCastOperator      = new Token<>( TypeEnum.PG_CAST_OPERATOR,"::");
    final public static Token<String> ConcatStringsPL     = new Token<>( TypeEnum.STRINGS_OPERATOR,"||");
    final public static Token<String> DoubleDot           = new Token<>( TypeEnum.DOUBLE_DOT,      "..");
    final public static Token<Character> Colon            = new Token<>( TypeEnum.COLON,           ':' );
    final public static Token<Character> Equals           = new Token<>( TypeEnum.COMPARISON_OPERATOR,'=' );
    final public static Token<Character> Minus            = new Token<>( TypeEnum.ARITHMETIC_OPERATOR,'-' );
    final public static Token<Character> Plus             = new Token<>( TypeEnum.ARITHMETIC_OPERATOR,'+' );
    final public static Token<String> Null                = new Token<>( TypeEnum.EXPRESSION, "NULL"   );
    final public static Token<String> On                  = new Token<>( TypeEnum.EXPRESSION, "on"     );
    final public static Token<String> Off                 = new Token<>( TypeEnum.EXPRESSION, "off"    );

    final public static Token<String> Pg_Fore             = new Token<>( TypeEnum.EXPRESSION, "$$"     );
    final public static Token<String> Format              = new Token<>( TypeEnum.EXPRESSION, "format" );
    final public static Token<String> Str                 = new Token<>( TypeEnum.EXPRESSION, "string" );
    final public static Token<String> End                 = new Token<>( TypeEnum.EXPRESSION, "end"    );
    final public static Token<String> Begin               = new Token<>( TypeEnum.EXPRESSION, "begin"  );
    //SQL
    final public static Token<String> OrderBy             = new Token<>( TypeEnum.ORDER_BY,  "ORDER BY");
    final public static Token<String> GroupBy             = new Token<>( TypeEnum.GROUP_BY,  "GROUP BY");
    final public static Token<String> Select              = new Token<>( TypeEnum.SELECT,    "SELECT"  );
    final public static Token<String> From                = new Token<>( TypeEnum.SELECT,    "FROM"    );
    final public static Token<String> Where               = new Token<>( TypeEnum.WHERE,     "WHERE"   );
    final public static Token<String> Having              = new Token<>( TypeEnum.HAVING,    "HAVING"  );

    final public static Token<String> Limit               = new Token<>( TypeEnum.LIMIT,     "LIMIT"   );
    final public static Token<String> Offset              = new Token<>( TypeEnum.OFFSET,    "OFFSET"  );
    final public static Token<String> Fetch               = new Token<>( TypeEnum.FETCH,     "FETCH"   );
    final public static Token<String> Top                 = new Token<>( TypeEnum.TOP,       "TOP"     );
    final public static Token<String> Union               = new Token<>( TypeEnum.TOP,       "UNION"   );
    final public static Token<String> Intersect           = new Token<>( TypeEnum.TOP,       "INTERSECT" );
    final public static Token<String> Except              = new Token<>( TypeEnum.TOP,       "EXCEPT"  );


    /** */
    private final TypeEnum type;

    /** */
    private final T        value;

    /** */
    public Token(TypeEnum type, T value ) {
        this.type  = type;
        this.value = value;
    }

    /** */
    public TypeEnum getType() {
        return type;
    }

    /** */
    public T getValue() {
        return value;
    }

    //
    public boolean isInteger() {
        return this.type == TypeEnum.INTEGER || this.type == TypeEnum.INTEGER_STRING;
    }

    //
    public boolean isNumeric() {
        return this.type == TypeEnum.DECIMAL || isInteger();
    }

    /** */
    public boolean isExpression() {
        return this.type == TypeEnum.EXPRESSION;
    }

    /** */
    @Override
    public String toString() {
        return type.name() + ": " + value;
    }

    /** */
    public static TypeEnum typeOf( Token<?> t )
    {
        return t == null ? TypeEnum.UNKNOWN  : t.type;
    }

    @Override
    public void accept( Appendable out ) {

        if (this.type == TypeEnum.UNCHANGED_MARK)
            return;

        try {

            if (U.in(this, Token.Dot, Token.Nl, Token.Semicolon, Token.ParenthesisLeft, Token.ParenthesisRight, Token.Comma)) {
                out.append(this.getValue().toString());
            }//end if
            else {

                if (U.in(getType(), TypeEnum.INTEGER, TypeEnum.DECIMAL, TypeEnum.STRING))
                    out.append(value2Str(getValue())).append(' ');
                else {
                    if (this.getType() == TypeEnum.ARITHMETIC_OPERATOR)
                        out.append(getValue().toString());
                    else
                        out.append(getValue().toString()).append(' ');
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

    /*
    @Override
    public void accept( StringBuilder sb ) {

        if( this.type == TypeEnum.UNCHANGED_MARK )
            return;

        char ch = sb.length() == 0 ? 0 : sb.charAt( sb.length() -1 );

        if( U.in( this, Token.Dot, Token.Nl, Token.Semicolon, Token.ParenthesisLeft, Token.ParenthesisRight, Token.Comma) )
        {
            if( U.inChar( ch, ' ', '\n' ) )
                sb.setCharAt( sb.length() - 1, (Character)getValue() );
            else
                sb.append( this.getValue() );
        }//end if
        else {

            if( U.in( getType(), TypeEnum.INTEGER, TypeEnum.DECIMAL, TypeEnum.STRING ) )
                sb.append( value2Str(getValue()) ).append(' ');
            else
            {
                if( this.getType() == TypeEnum.ARITHMETIC_OPERATOR )
                    sb.append( getValue() );
                else
                    sb.append( getValue() ).append(' ');
            }
        }
    }
}
*/


/** текущий скан контекст
public interface IContext {
    char current ( );
    char next    ( );
    char previous( );
    int  lineNum ( );
    int  symbNum ( );
    boolean eof  ( );
    boolean move ( );
    // mark
    // reset
    // unget
    // peek
}
*/