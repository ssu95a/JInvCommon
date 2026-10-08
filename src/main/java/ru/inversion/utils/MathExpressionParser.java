package ru.inversion.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** */
public class MathExpressionParser {

    /** */
    public enum ItemTypeEnum {
        Plus, Minus, Mltpl, Divide, Digit, Comment, Variable
    }

    /** */
    public static class Item {

        private final ItemTypeEnum type;
        private final Object       value;

        public Item( ItemTypeEnum type, Integer value ) {
            this.type = type;
            this.value= value;
        }
        public Item( ItemTypeEnum type, String value ) {
            this.type = type;
            this.value= value;
        }
        public Item( ItemTypeEnum type ) {
            this.type = type;
            this.value= null;
        }

        public ItemTypeEnum getType() {
            return type;
        }

        public <T> T getValue() {
            return (T)value;
        }

        @Override
        public String toString() {
            return "[type=" + type +", value=" + value +"]";
        }
    }


    /** */
    private static int parseComment( char[] a, int index, List<Item> items )
    {
        if( index + 1 < a.length && a[index + 1] == '*')
        {
            final StringBuilder sb = new StringBuilder();
            int i = index + 2;
            for( ; i < a.length; i++ )
            {
                if( a[i] == '/' && a[i-1]=='*' )
                {
                    i--;
                    if( sb.length() > 0) {
                        sb.deleteCharAt( sb.length() - 1 );
                        items.add( new Item(ItemTypeEnum.Comment, sb.toString() ) );
                    }
                    break;
                }
                sb.append(a[i]);
            }
            index = i;
        }
        return index;
    }

    /** */
    public static List<Item> parse( String expr )
    {
        List<Item> items = new ArrayList<>();

        // 24 + 999 *9 -10*7+11
        //boolean isDigit = false;
        StringBuilder sb = null;

        //boolean insideComment = false;

        final char[] a = expr.toCharArray();

        for( int i = 0 ; i < a.length; i++ )
        {
             char ch = a[i];

             if( ch == '/' )
             {
                 i = parseComment( a, i, items );
                 continue;
             }

             if( Character.isSpaceChar(ch) ) {
                 if( sb != null ) {
                     items.add( new Item(ItemTypeEnum.Digit, Integer.parseInt(sb.toString() )) );
                     sb = null;
                 }
                 continue;
             }

            if( Character.isDigit(ch) ) {
                if( sb == null )
                    sb = new StringBuilder();
                sb.append(ch);

                continue;
            }

            if( sb != null ) {
                items.add( new Item(ItemTypeEnum.Digit, Integer.parseInt(sb.toString() )) );
                sb = null;
            }

             switch( ch ) {
                 case '+': items.add( new Item(ItemTypeEnum.Plus   ));break;
                 case '-': items.add( new Item(ItemTypeEnum.Minus  ));break;
                 case '*': items.add( new Item(ItemTypeEnum.Mltpl  ));break;
                 case '/': items.add( new Item(ItemTypeEnum.Divide ));break;
             }
        }
        if( sb != null ) {
            items.add( new Item(ItemTypeEnum.Digit, Integer.parseInt(sb.toString() )) );
        }
        return items;
    }

    public static void main( String[] args ) {

        Function<String,Integer> supplier = new Function< String, Integer >() {
            @Override
            public Integer apply( String s ) {
                if( "p1".equalsIgnoreCase(s) )
                    return 11;
                return 0;
            }
        };

        final List<Item> items2 = new ArrayList<>();
        {
        final List<Item> items  = parse("5*7-8+4-7*2 /*100/8*/ +10/2 + p1");
        for( int i = 0; i < items.size(); i++ )
        {
            Item item = items.get(i);

            if( item.getType() == ItemTypeEnum.Comment )
                continue;

            if( item.getType() == ItemTypeEnum.Mltpl || item.getType() == ItemTypeEnum.Divide ) {
                Item p1 = items2.get( items2.size() -1 );
                Item p2 = items.get ( i+1 );
                Integer v = item.getType() == ItemTypeEnum.Mltpl ? p1.<Integer>getValue() * p2.<Integer>getValue() : p1.<Integer>getValue() / p2.<Integer>getValue();
                items2.set( items2.size() -1 , new Item( ItemTypeEnum.Digit, v ) );
                i++;
                continue;
            }

            items2.add( item );
        }
        }

        Integer value = items2.get(0).getValue();

        for( int i = 1; i < items2.size(); i+=2 )
        {
            Item item = items2.get(i);

            if( item.getType() == ItemTypeEnum.Plus)
                value += items2.get(i+1).<Integer>getValue();
            if( item.getType() == ItemTypeEnum.Minus)
                value -= items2.get(i+1).<Integer>getValue();
        }


        System.out.println( value );
    }

}
