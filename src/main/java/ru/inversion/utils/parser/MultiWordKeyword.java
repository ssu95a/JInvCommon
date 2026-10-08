package ru.inversion.utils.parser;

import ru.inversion.utils.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** */
public class MultiWordKeyword {

    // Статический класс для хранения записей
    private static class KeywordEntry {
        final String[] words;
        final Token<String> token;

        KeywordEntry( String[] words, Token<String> token) {
            this.words = words;
            this.token = token;
        }
    }

    // Отсортированный массив ключевых слов
    private static final KeywordEntry[] KEYWORDS;

    // Статический блок инициализации с сортировкой
    static {

        final List<KeywordEntry> entries = new ArrayList<>();

        // Добавляем все ключевые слова с непосредственным созданием NewToken
        entries.add( new KeywordEntry(new String[]{"select"}, Token.Select));
        entries.add( new KeywordEntry(new String[]{"from"  }, Token.From  ));
        entries.add( new KeywordEntry(new String[]{"where" }, Token.Where ));
        entries.add( new KeywordEntry(new String[]{"union" }, Token.Union ));
        entries.add( new KeywordEntry(new String[]{"limit" }, Token.Limit ));
        entries.add( new KeywordEntry(new String[]{"offset"}, Token.Offset));
        entries.add( new KeywordEntry(new String[]{"fetch" }, Token.Fetch ));
        entries.add( new KeywordEntry(new String[]{"top"   }, Token.Top   ));
        entries.add( new KeywordEntry(new String[]{"intersect"},Token.Intersect ));
        entries.add( new KeywordEntry(new String[]{"except"}, Token.Except));
        entries.add( new KeywordEntry(new String[]{"order", "by"}, Token.OrderBy ));
        entries.add( new KeywordEntry(new String[]{"group", "by"}, Token.GroupBy ));
        entries.add( new KeywordEntry(new String[]{"having"}, Token.Having ));
        entries.add( new KeywordEntry(new String[]{"inner", "join"},  new Token<>(Token.TypeEnum.EXPRESSION, "INNER JOIN")));
        entries.add( new KeywordEntry(new String[]{"left",  "join"},  new Token<>(Token.TypeEnum.EXPRESSION, "LEFT JOIN")));
        entries.add( new KeywordEntry(new String[]{"left",  "outer", "join"}, new Token<>(Token.TypeEnum.EXPRESSION, "LEFT OUTER JOIN")));
        entries.add( new KeywordEntry(new String[]{"right", "join"},  new Token<>(Token.TypeEnum.EXPRESSION, "RIGHT JOIN")));
        entries.add( new KeywordEntry(new String[]{"right", "outer", "join"}, new Token<>(Token.TypeEnum.EXPRESSION, "RIGHT OUTER JOIN")));
        entries.add( new KeywordEntry(new String[]{"union", "all" },  new Token<>(Token.TypeEnum.EXPRESSION, "UNION ALL")));
        entries.add( new KeywordEntry(new String[]{"not",   "in"  },  new Token<>(Token.TypeEnum.EXPRESSION, "NOT IN")));
        entries.add( new KeywordEntry(new String[]{"not",   "exists"},new Token<>(Token.TypeEnum.EXPRESSION, "NOT EXISTS")));
        entries.add( new KeywordEntry(new String[]{"is",    "null"},  new Token<>(Token.TypeEnum.EXPRESSION, "IS NULL")));
        entries.add( new KeywordEntry(new String[]{"is",    "not" },  new Token<>(Token.TypeEnum.EXPRESSION, "IS NOT")));
        entries.add( new KeywordEntry(new String[]{"cross", "join"},  new Token<>(Token.TypeEnum.EXPRESSION, "CROSS JOIN")));
        entries.add( new KeywordEntry(new String[]{"full",  "join"},  new Token<>(Token.TypeEnum.EXPRESSION, "FULL JOIN")));

        // Сортируем: лексикографически по словам, при равенстве — более короткие ключи раньше
        entries.sort(new Comparator< KeywordEntry >() {
            @Override
            public int compare( KeywordEntry a, KeywordEntry b ) {
                int minLength = Math.min(a.words.length, b.words.length);
                for(int i = 0; i < minLength; i++)
                {
                    int cmp = a.words[i].compareToIgnoreCase(b.words[i]);
                    if (cmp != 0)
                        return cmp;
                }
                return Integer.compare(a.words.length, b.words.length);
            }
        });

        KEYWORDS = entries.toArray( new KeywordEntry[0]);
    }

    // Строгий линейный проход по KEYWORDS.
    public static Pair<Token<String>, Integer> get(Token<?>[] tokens )
    {
        if( tokens == null || tokens.length == 0 )
            return null;


        Token<String> bestToken = null;

        int bestLen = 0;

        for( KeywordEntry e : KEYWORDS )
        {
            final int kLen = e.words.length;

            // уже найдено совпадение длиннее или равное — этот кандидат не улучшит результат
            if( kLen <= bestLen )
                continue;

            if( matches(tokens, e.words) )
            {
                bestToken = e.token;
                bestLen = kLen;
            }
        }

        return (bestToken == null) ? null : Pair.makePair(bestToken, bestLen);
    }

    /** */
    private static boolean matches( Token<?>[] tokens, String[] keywordWords) {

        if( tokens.length < keywordWords.length)
            return false;


        for( int i = 0; i < keywordWords.length; i++ )
        {
            final Token<?> t = tokens[i];

            if( t == null || !t.isExpression() )
                return false;

            final String tokenWord = String.valueOf(t.getValue());

            if( !tokenWord.equalsIgnoreCase(keywordWords[i]) )
                return false;
        }

        return true;
    }

}