package ru.inversion.parser.token;

import ru.inversion.utils.U;

import java.util.function.Consumer;

import static ru.inversion.dataset.parser.SQLParser.value2Str;

/**
 *
 * @author ssu
 */
public class Token<T> implements Consumer<StringBuilder> {

    /** */
    public enum TypeEnum {

        EXPRESSION,

        INDEXED_PARAMETER,

        NAMED_PARAMETER,

        STRING,

        COMMENT,

		INTEGER,

		UNTOUCHABLE_INTEGER,

		DECIMAL,

        QUOTED_TEXT,

        PUNCTUATION,

        ARITHMETIC_OPERATOR,

        COMPARISON_OPERATOR,

        UNCHANGED_MARK,

        CONCAT_STRINGS_OPERATOR,

        PARENTHESIS_LEFT,

        PARENTHESIS_RIGHT,

        COMMA,     // запятая

        SEMICOLON, // точка с запятой

        DOT,       // точка

        DOUBLE_DOT,// двойная точка

        NL,        // new line

        TAB,

        NAMED_NOTATION, //=>

        ASSIGNMENT, // :=

        OPTIMIZER_HINT,

        ORDER_BY,

        GROUP_BY,

        LEFT_BRACE, // {

        RIGHT_BRACE,// }

        WHERE,

        SELECT,

        HAVING,

        FROM,

        PG_CAST_OPERATOR, // ::

        END,

        LIMIT,

        OFFSET,

        WITH
    }

    final public static Token<Character> Comma            = new Token<>( TypeEnum.COMMA, ','           );
    final public static Token<Character> Dot              = new Token<>( TypeEnum.DOT,   '.'           );
    final public static Token<Character> Semicolon        = new Token<>( TypeEnum.SEMICOLON,';'        );
    final public static Token<Character> Nl               = new Token<>( TypeEnum.NL,    '\n'          );
    final public static Token<Character> Tab              = new Token<>( TypeEnum.TAB,   '\t'          );
    final public static Token<String> NamedNotation       = new Token<>( TypeEnum.NAMED_NOTATION, "=>" );
    final public static Token<String> Assignment          = new Token<>( TypeEnum.ASSIGNMENT, ":="     );
    final public static Token<Character> ParenthesisLeft  = new Token<>( TypeEnum.PARENTHESIS_LEFT, '(');
    final public static Token<Character> ParenthesisRight = new Token<>( TypeEnum.PARENTHESIS_RIGHT,')');
    final public static Token<Character> LeftBrace        = new Token<>( TypeEnum.LEFT_BRACE, '{'      );
    final public static Token<Character> RightBrace       = new Token<>( TypeEnum.RIGHT_BRACE,'}'      );
    final public static Token<Character> IndexedParameter = new Token<>( TypeEnum.INDEXED_PARAMETER,'?');
    final public static Token<Character> UnchangedMark    = new Token<>( TypeEnum.UNCHANGED_MARK,'~'   );
    final public static Token<String> OrderBy             = new Token<>( TypeEnum.ORDER_BY, "ORDER BY" );
    final public static Token<String> GroupBy             = new Token<>( TypeEnum.GROUP_BY, "GROUP BY" );
    final public static Token<String> Where               = new Token<>( TypeEnum.WHERE,    "WHERE"    );
    final public static Token<String> Select              = new Token<>( TypeEnum.SELECT,   "SELECT"   );
    final public static Token<String> Perform             = new Token<>( TypeEnum.SELECT,   "perform"  );
    final public static Token<String> Having              = new Token<>( TypeEnum.HAVING,   "HAVING"   );

    final public static Token<String> From                = new Token<>( TypeEnum.FROM,     "FROM"     );
    final public static Token<String> PgCastOperator      = new Token<>( TypeEnum.PG_CAST_OPERATOR,"::");

    final public static Token<String> End                 = new Token<>( TypeEnum.END, "END"           );

    final public static Token<String> Do                  = new Token<>( TypeEnum.EXPRESSION, "DO"     );

    final public static Token<String> Pg_Fore             = new Token<>( TypeEnum.EXPRESSION, "$do$"   );

    final public static Token<String> Declare             = new Token<>( TypeEnum.EXPRESSION, "DECLARE");
    final public static Token<String> Begin               = new Token<>( TypeEnum.EXPRESSION, "BEGIN"  );

    /** */
    private final TypeEnum type;

    /** */
    private final T        value;

    /** */
    public Token( TypeEnum type, T value ) {
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

    /** */
    @Override
    public String toString() {
        return type.name() + ": " + value;
    }

    /** */
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

            if( U.in( getType(), TypeEnum.INTEGER, TypeEnum.UNTOUCHABLE_INTEGER, TypeEnum.DECIMAL, TypeEnum.STRING ) )
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
