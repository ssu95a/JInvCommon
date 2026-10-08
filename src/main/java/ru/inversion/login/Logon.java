package ru.inversion.login;

import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.AbstractDataSourceCfgBuilder;
import ru.inversion.db.session.SessionEnvironment;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.xml.bind.DatatypeConverter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** */
public class Logon {

    public static final String USER_ID = "ru.inversion.userid";

    static final private Pattern namedPattern = Pattern.compile("^--[\\S]*=[\\S]+");

    /** */
    private static class CLParameters {

        //
        final private Map<String,String> named;
        //
        final private Set<String>        unnamed;

        public CLParameters( String[] args )
        {
            if( args == null || args.length == 0 )
            {
                named   = Collections.emptyMap();
                unnamed = Collections.emptySet();
            }
            else
            {
                Map<String,String> namedMap = null;
                Set<String> unnamedSet = null;

                for( String arg : args )
                {
                    if( namedPattern.matcher(arg).find() )
                    {
                        if( namedMap == null )
                            namedMap = new HashMap<>();

                        //final String[] a = arg.substring(2).split("=",0);
                        int index = arg.indexOf('=');

                        namedMap.put(arg.substring(2, index), arg.substring(index+1) );
                    }
                    else
                    {
                        if( unnamedSet == null )
                            unnamedSet = new HashSet<>();

                        unnamedSet.add(arg);
                    }
                }

                named   = U.nvl( namedMap, Collections.emptyMap()  );
                unnamed = U.nvl( unnamedSet, Collections.emptySet() );

            }//end else
        }

        /** */
        public String getNamed( String name )
        {
            return named.get(name);
        }

        /** */
        public boolean hasUnNamed( String name )
        {
            return unnamed.contains(name);
        }
    }

    /** */
    public static void login( String[] args )
    {
        final CLParameters clParameters = new CLParameters(args);

        String userId = clParameters.getNamed(USER_ID);

        if( S.isNullOrEmpty(userId) || userId.length() < 3)
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Error on command string parameters. No " + USER_ID + ", or bad format.");

        boolean pseudoConnection = true,
                hasLoginData     = false;

        switch( userId.charAt(0 )) {
            case 'T':
            case 't':
                pseudoConnection = true;
                hasLoginData = true;
                break;
            case 'F':
            case 'f':
                pseudoConnection = false;
                hasLoginData = true;
                break;
        }

        if( !hasLoginData )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Error on command string parameters. '" + USER_ID + "' bad format." );

        hasLoginData = false;

        userId = userId.substring(1);

        String login = null, password = null, dbUrl = null;

        try {

            String connectString = new String( DatatypeConverter.parseBase64Binary(userId) );

            int i1 = connectString.indexOf("/");
            int i2 = connectString.lastIndexOf("@");

            if(i1 > 0 && i2 > 0) {

                login    = connectString.substring(0, i1);
                password = connectString.substring(i1 + 1, i2);
                dbUrl    = connectString.substring(i2 + 1);

                hasLoginData = !U.containsNull( login, password, dbUrl );
            }

        } catch( Exception ex ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on parse userId: ", ex );
        }

        if( !hasLoginData )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Error on command string parameters. '" + USER_ID + "' bad format.");

        try {

            System.out.println (
                Tags.PRODUCT_LABEL +
                "login by ..." +
                "\n  user    : " + login +
                "\n  password: " + S.space( password.length(), '*') +
                "\n  db_url  : " + dbUrl +
                "\n  pseudo  : " + pseudoConnection
//                "\n  middle  : " + ( middleDrvServer != null ? "on, " : "off" ) +
//                ( middleDrvServer == null ? "" : "url : " + middleDrvServer ) +
//                "\n  token   : " + useToken
            );

            final String s1 = dbUrl;

            final AbstractDataSourceCfgBuilder dscb = AbstractDataSourceCfgBuilder.create( new Function<String, String>() {
                @Override
                public String apply( String s ) {

                    if( s == null )
                        return null;

                    switch(s) {
                        case "app_id": return "Inv-Console-Login";
                        case "db_url":
                        case "url"   :
                            return s1;
                    }

                    return null;
                }
            });

            dscb.property( "check_connection", true );

            final DataSourceCfg dsc = dscb.login( login )
                    .password( password )
                    .dbUrl   ( dbUrl    )
                    .usePseudoConnection( pseudoConnection )
                        .build( );

            SessionEnvironment.initialize( dsc );

        } catch ( Throwable th ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on login in database.", th );
        }
    }
}
