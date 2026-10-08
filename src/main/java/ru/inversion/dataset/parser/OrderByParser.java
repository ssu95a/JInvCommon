package ru.inversion.dataset.parser;

import ru.inversion.utils.ReaderScanner;
import ru.inversion.utils.S;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author ssu
 */
public class OrderByParser {

    /** */
    static private interface IState { 
        boolean process( ReaderScanner.IContext context );
    }
    
    /** */
    static private class ParenthesisState implements IState {
        
        private boolean eol = false;
        private int   level  = 1;
        /** */
        @Override
        public boolean process( ReaderScanner.IContext context ) {
            
            if( eol )
                return true;

            if( context.current() == '(' )
                level++;
            else
                if( context.current() == ')' )
                    eol = --level == 1;

            return false;
        }
        
    }
    
    /** */
    static private class CommentState implements IState
    {
        private boolean	first	  = true;
        private boolean	eof		  = false;
        private boolean	multiLine = false;

        public CommentState( ) {
        }

        /** */
        @Override
        public boolean process( ReaderScanner.IContext context ) 
        {
            if( eof )
                return true;

            if( first ) {
                multiLine = ( context.current() == '/' && context.next() == '*' );
                first = false;
            }

            if( multiLine ) {

                if( context.previous() == '*' && context.current() == '/' )
                    eof = true;
            }
            else 
            {
                if( context.current() == '\n' )
                    eof = true;
            }

            return false;
        }
    }
    
    /** */
    public static List<String> parseAndGetColumnsList( String expr ) {
        
        if( S.isNullOrEmpty(expr) ) 
            return Collections.<String>emptyList();

        if( expr.indexOf(',') == -1 )
            return Collections.singletonList(expr);

        List<String> retList = new ArrayList<>();
        
    	IState currentState = null;
        
        StringBuilder sb = new StringBuilder();

        boolean doAddRetItem = true;
        
        for( ReaderScanner.IContext c : ReaderScanner.newIterable(expr) ) {
                
            repeat: {
                
                if( currentState == null ) {

                    if ( 
                        ( c.current() == '/' && c.next() == '*' )
                        ||       
                        ( c.current() == '-' && c.next() == '-' )   
                    )
                        currentState = new CommentState();
                    else
                        if( c.current() == '(' )
                            currentState = new ParenthesisState();
                }

                if( currentState != null ) {

                    doAddRetItem = currentState.process( c );

                    if( doAddRetItem ) {
                        
                        currentState = null;
                        
                        break repeat;
                    }
                }
            }
            
            if( doAddRetItem && c.current() == ',' ) {

                retList.add( sb.toString().trim() );

                sb = new StringBuilder();

                continue;
            }
                
            sb.append( c.current() );
        }
        
        // last item
        retList.add( sb.toString().trim() );
        
        return retList;
    }
    
    /** */
    private OrderByParser() {}
    
    /** */
    public static void main(String[] args) {
        
        String test2 = "CASE WHEN dTrcDoc < Date '2013-12-14' " + 
                       "AND cNalFlag IS NOT NULL THEN 5 WHEN dTrcDoc < Date '2013-12-14' AND iTrcPriority > 3 THEN iTrcPriority - 1 ELSE iTrcPriority END" + 
                       ", dTrcCreate, iTrcNum, iTrcANum," +
                "(to_char(nvl(PARENT_ID,int_id) ,'fm099999999')||to_char(nvl(parent_id,'000000000'),'fm099999999')), 1, 2, 3, (3), 2, dTrcDoc/*,21*/, c(1,1) s, iTrcANum(1,2),,, ";

        String test = "2, dTrcDoc/*,21*/, c(1,1) s, iTrcANum(1,2),,,";
        
        try {

            List<String> parts = OrderByParser.parseAndGetColumnsList(test2);

            parts.forEach(System.out::println);
            
            
        } catch (Throwable ex) {
            //JInvErrorService.handleException(null, ex);
            ex.printStackTrace();
        }
    }
    
}
