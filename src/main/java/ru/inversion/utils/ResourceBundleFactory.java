package ru.inversion.utils;

import java.util.Collections;
import java.util.Enumeration;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 *
 * @author ssu
 */
public class ResourceBundleFactory {

    /** */
    private static final ResourceBundle STUB_BUNDLE = new ResourceBundle() 
    {
        public Enumeration<String> getKeys()             { return Collections.emptyEnumeration(); }
        protected Object handleGetObject(String key)     { return key; }
        public String toString()                         { return "NONEXISTENT_BUNDLE"; }
        @Override public boolean containsKey(String key) { return true; }
    };
    
    /** */
    static private ResourceBundleFactory g_instance = new ResourceBundleFactory();
    
    /** */
    static public ResourceBundleFactory INSTANCE() { return g_instance; }
    
    /** */
    private ResourceBundleFactory() {}

    /** */
    public ResourceBundle getBundle( String name ) {
        try {
            return ResourceBundle.getBundle( name );
        }
        catch( Throwable th ) {
            System.err.println( th.getLocalizedMessage() );
            //th.printStackTrace();
        }
        return STUB_BUNDLE;
    }

    /** */
    public ResourceBundle getBundle( String name, ResourceBundle.Control control ) {
        try {
            return ResourceBundle.getBundle( name, control );
        }
        catch( Throwable th ) {
            System.err.println( th.getLocalizedMessage() );
            //th.printStackTrace();
        }
        return STUB_BUNDLE;
    }

    /** */
    public ResourceBundle getBundle( Class forClass ) { 
        try {
            
            try {
                
                String bundleName = forClass.getName();
                
                int index = bundleName.lastIndexOf('.');
                if( index != -1 )
                    bundleName = S.insert( bundleName, index, ".res" );
                
                return ResourceBundle.getBundle( bundleName );
            }
            catch( MissingResourceException mre ) {
                return ResourceBundle.getBundle( forClass.getName() );
            }
        }
        catch( Throwable th ) {
            System.err.println( th.getLocalizedMessage() );
            //th.printStackTrace();
        }
        return STUB_BUNDLE;
    }

}
