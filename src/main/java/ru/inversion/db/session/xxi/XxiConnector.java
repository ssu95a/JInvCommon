package ru.inversion.db.session.xxi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.utils.Holder;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import javax.xml.bind.DatatypeConverter;
import java.net.PasswordAuthentication;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;
import java.util.function.BiFunction;

import static ru.inversion.db.session.AbstractDataSourceCfgBuilder.V$SESSION_PROGRAM;
import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.INTERNAL_ERROR;
import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.PASSWORD_CHANGE_REQUIRED;

/**
 *
 * */
public class XxiConnector {

    /** */
    final private static Logger logger = LoggerFactory.getLogger( XxiConnector.class );

    /** */
    static char[] rDefUserPass = {
            89, 121, 106, 96, 100, 112, 79, 111,
            102, 68, 117, 48, 116, 114, 103, 104,
            115, 122, 55, 120, 65, 101, 112, 112,
            110, 51, 115, 118, 109, 102, 123, 46,
            103, 112, 115, 102, 119, 102, 115, 80,
            115, 98, 100, 109, 102, 84, 118, 113,
            113, 112, 115, 117, 84, 102, 115, 119,
            106, 100, 102, 116, 0
    };

    /** */
    static char[] rDefKey = {81, 84, 105, 65, 74, 111, 119, 102, 115, 116, 106, 112, 111, 47, 115, 118, 0 };

    /** */
    private XxiConnector( ) {
    }

    /** */
    private PasswordAuthentication initPseudoLoginAndPassword( ) {

        String ls = My_Convert(rDefUserPass);

        int index = ls.indexOf('@');
               ls = ls.substring( 0, index );
            index = ls.indexOf('/');

        String[] ra = new String[2];

        ra[0] = ls.substring( 0, index );
        ra[1] = ls.substring( index + 1, ls.length() );

        return new PasswordAuthentication( ra[0], ra[1].toCharArray() );
    }

    /** */
    private String decodeString( String encodingFrom, String encodingTo, String value ) throws SQLException {

        try {

            CharBuffer charBuffer = CharBuffer.wrap(value);

            Charset charsetTo   = Charset.forName(encodingTo);
            Charset charsetFrom = Charset.forName(encodingFrom);

            return charsetTo.newDecoder().decode( charsetFrom.newEncoder().encode(charBuffer) ).toString();
        }
        catch( Throwable th ) {
            throw new SQLException("[Xxi-Connector] - Error on decode string from '" + encodingFrom + "' to '" + encodingTo + "'", th );
        }
    }

    /** */
    private static void check4TokenData( Connection c, Properties p ) throws SQLException {

        final String string_check = p.getProperty( "ru.inversion.token.string_check"  );
        final String string_signed= p.getProperty( "ru.inversion.token.string_signed" );

        try ( CallableStatement st = c.prepareCall ("{? = call xxi_user.check_token_certificate( ?, ? ) }") )
        {
            st.registerOutParameter( 1, Types.VARCHAR );
            st.setString           ( 2, string_check  );
            st.setString           ( 3, string_signed );

            st.executeUpdate();

            final Object ret = st.getObject(1);

            if( ret != null )
                throw new SQLException("[Xxi-Connector] - " + ret.toString() );
        }
    }


    /** */
    public static PasswordAuthentication login (
        String jdbcUrl,
        String login,
        String pwd,
        Properties properties,
        BiFunction<Connection,Object,Object> resultHandler
    )
        throws SQLException
    {
        XxiConnector x = new XxiConnector();

        if( properties.getProperty("v$session.program") == null )
            properties.put("v$session.program", "XxiConnector" );

        Properties connectionProperties = new Properties( properties );
        {
            PasswordAuthentication pseudoAuthentication = x.initPseudoLoginAndPassword();

            connectionProperties.setProperty("user",     pseudoAuthentication.getUserName());
            connectionProperties.setProperty("password", String.valueOf( pseudoAuthentication.getPassword() ) );
        }

        final Holder<PasswordAuthentication> realAuthentication = new Holder<>();
        final Holder<String> info = new Holder<>();
        //String info[] = new String[1];

        final boolean useToken = "true".equals( properties.getProperty("ru.inversion.use_token", "false") );

        int resultCode = x.connectAndGetRealPassword( jdbcUrl, login, pwd, connectionProperties, resultHandler, realAuthentication, info, useToken );

        switch( resultCode ) {
            case 0:
            case 2:
            case 1:
                ; // realAuthentication = new PasswordAuthentication( login, info[0].toCharArray() );
            break;
                //throw new SQLException("[Xxi-Connector] - To log into cABS 'XXI Vek' you must to change the password.");
            case -1:
                throw new SQLException("[Xxi-Connector] - " + info.get() );
            default:
                throw new SQLException("[Xxi-Connector] - Unknown return value from 'XXI_LOGON': " + resultCode );
        }

        if( realAuthentication.isPresent() )
        {
            connectionProperties.setProperty( "user",     realAuthentication.get().getUserName()                 );
            connectionProperties.setProperty( "password", String.valueOf(realAuthentication.get().getPassword()) );

        }

        try( Connection c = DriverManager.getConnection( jdbcUrl, connectionProperties ) ) {

            if( resultCode == 2 && resultHandler != null ) {

                if( (Boolean)resultHandler.apply(c,2) ) {
                    ;//return String.valueOf( realAuthentication.getPassword() );
                }
                else
                    throw new SQLException("[Xxi-Connector] - No PIN - no entry in cABS!" );
            }
        }
        catch( SQLException ex ) {
            // Признак что была смена пароля
            // Не можем соединиться т.к. скорее всего был вход через псевдосоединение
            if( resultCode == 1 ) {
                throw new SQLException("[Xxi-Connector] - Для сохранения изменений необходимо осуществить повторный вход в систему!" );
            }

            throw ex;
        }

        return realAuthentication.get();
    }

    /** */
    static void check4XxiUpgrade( Connection c, Properties p ) throws SQLException {

        if( "true".equals( p.getProperty("ru.inversion.check_xxi_upgrade_disable", "false") ) )
            return;

        boolean enableUpgrade = false;

        try( final PreparedStatement ps = c.prepareStatement(
                "select OBJECT_ID from all_objects where owner = 'PUBLIC' and object_name = 'XXI_UPGRADE' and OBJECT_TYPE = 'SYNONYM'") ) {

            try( final ResultSet rs = ps.executeQuery() ) {
                  enableUpgrade = rs.next();
            }
        }
        catch( Throwable th ) {
            System.err.println("Error on check4XxiUpgrade:");
            th.printStackTrace();
        }

        if( enableUpgrade ) {
            throw new SQLException("[Xxi-Connector] - Технический перерыв. В ЦАБС ведутся системные работы!");
        }
    }


    /** */
    public static PasswordAuthentication serverSideLogin( Connection techConnection, PasswordAuthentication auth, Properties properties )
    {
        if( properties == null )
            properties = new Properties();

        if( properties.getProperty("v$session.program") == null )
            properties.put("v$session.program", "XxiConnector.serverSide" );

        final boolean useToken = "true".equals(properties.getProperty("ru.inversion.use_token", "false"));
        if (useToken)
            throw XXIConnectorException.of( INTERNAL_ERROR, "Token login is not supported", null, U.toMap("login", auth.getUserName(), "op", "token_login"));


        try {

            XxiConnector connector = new XxiConnector();

            final String key = connector.My_Convert (rDefKey);
            arc4engine ctx = new arc4engine ();
            ctx.init( key.toCharArray () );

            check4XxiUpgrade( techConnection, properties );

            int resultCode;

            PasswordAuthentication retAuth = null;

            try ( CallableStatement st = techConnection.prepareCall ("{? = call XXI.XXI_LOGON.XXI_LOGON2(?, ?, ?, ?) }") )
            {
                int i = 1;
                st.registerOutParameter( i++, Types.INTEGER        );
                st.setString           ( i++, DATABASE_CHARSET8BIT );

                String lpwd_s = ( auth.getUserName() + "/" + String.valueOf( auth.getPassword() ) );

                byte sh = (byte) ( System.currentTimeMillis() % 10 );
                for( int ii = 0; ii < sh; ii ++ )
                     ctx.arc4Next ();

                byte[] arc4c = ctx.arc4b( lpwd_s.getBytes (JAVA_CHARSET8BIT) );

                byte[] lpwd = new byte [arc4c.length + 2];
                lpwd [0] = 0;
                lpwd [1] = sh;

                System.arraycopy( arc4c, 0, lpwd, 2, arc4c.length );

                st.setBytes( i++, lpwd );
                st.registerOutParameter( i++, Types.BINARY );
                st.registerOutParameter( i++, Types.VARCHAR);

                st.execute ();

                resultCode = st.getInt (1);

                byte[] arc4c1 = ctx.arc4b( st.getBytes (4) );
                String out_c  = new String( arc4c1, JAVA_CHARSET8BIT );
                String out_m  = st.getString(5);

                switch( resultCode ) {
                    case 0:
                    case 2:
                    {
                        i = out_c.indexOf ('/');

                        retAuth =
                            new PasswordAuthentication (
                                out_c.substring ( 0,  i ),
                                out_c.substring ( i + 1 ).toCharArray()
                        );

                    }
                    break;
                    case 1:
                        throw XXIConnectorException.of( PASSWORD_CHANGE_REQUIRED, "Password change required", null, U.toMap("login", auth.getUserName(), "op", "serverSideLogin"));
                    default:
                        throw new SQLException("Error value resultCode = " + resultCode + " from 'XXI.XXI_LOGON.XXI_LOGON2'");
                    /*
                    case -1:
                    case -20566:
                    {
                        resultCode = -1;
                    }
                     */
                }//end switch
            }

            return retAuth;

        } catch (SQLException e) {
            throw XXIConnectorException.fromSql(e, U.toMap("login", auth.getUserName(), "mode", "serverSide", "op", "serverLogin"));
        }
    }


    /** */
    private int connectAndGetRealPassword (
        String jdbcUrl, String login, String pwd,
        Properties p,
        BiFunction<Connection,Object,Object> resultHandler,
        Holder<PasswordAuthentication> holderAuth,
        Holder<String> msg,
        boolean useToken
    )
        throws SQLException
    {
        boolean loginXxiOk = false;

        try
        {
            int resultCode;

            final String key = My_Convert (rDefKey);
            arc4engine ctx = new arc4engine ();
            ctx.init( key.toCharArray () );

            try( final Connection connection = DriverManager.getConnection( jdbcUrl, p ) )
            {
                loginXxiOk = true;

                check4XxiUpgrade( connection, p );

                try( CallableStatement st = connection.prepareCall ("{? = call XXI.XXI_LOGON.XXI_LOGON2(?, ?, ?, ?) }") )
                {
                    int i = 1;
                    st.registerOutParameter( i++, Types.INTEGER        );
                    st.setString           ( i++, DATABASE_CHARSET8BIT );

                    String lpwd_s = useToken ? ( "/" + login ) : ( login + "/" + pwd ); //.toUpperCase() убрано

                    //System.out.println( "login: " + lpwd_s );

                    byte sh = (byte) ( System.currentTimeMillis() % 10 );
                    for( int ii = 0; ii < sh; ii ++ )
                         ctx.arc4Next ();

                    byte[] arc4c = ctx.arc4b( lpwd_s.getBytes (JAVA_CHARSET8BIT) );

                    byte[] lpwd = new byte [arc4c.length + 2];
                    lpwd [0] = 0;
                    lpwd [1] = sh;

                    System.arraycopy( arc4c, 0, lpwd, 2, arc4c.length );

                    st.setBytes( i++, lpwd );
                    st.registerOutParameter( i++, Types.BINARY );
                    st.registerOutParameter( i++, Types.VARCHAR);

                    st.execute();

                    resultCode = st.getInt (1);

                    byte[] arc4c1 = ctx.arc4b( st.getBytes (4) );
                    String out_c  = new String( arc4c1, JAVA_CHARSET8BIT );

                    String out_m  = st.getString(5);

                    switch( resultCode ) {
                        case 0:
                        case 2:
                        {
                            i = out_c.indexOf ('/');

                            holderAuth.set (
                                new PasswordAuthentication (
                                    out_c.substring ( 0,  i ),
                                    out_c.substring ( i + 1 ).toCharArray()
                                )
                            );

                            //info [0] = out_c.substring ( i + 1 );
                        }
                        break;
                        case 1:
                        {
                            // Проверка на правильность переданного пароля,
                            // чтоб показать диалог смены пароля
                            {
                                i = out_c.indexOf('/');

                                holderAuth.set (
                                    new PasswordAuthentication (
                                        out_c.substring ( 0,  i ),
                                        out_c.substring ( i + 1 ).toCharArray()
                                    )
                                );

                                Properties tryProp = new Properties( p );
                                tryProp.setProperty( "password", String.valueOf( holderAuth.get().getPassword() ) );
                                tryProp.setProperty( "user", holderAuth.get().getUserName() );

                                try( Connection c = DriverManager.getConnection( jdbcUrl, tryProp ) ) {
                                    ;
                                }
                                catch( SQLException ex ) {
                                    if( ex.getErrorCode() == 28001 ) {
                                        // если пароль устарел, то не ошибка
                                        // просто меняем его
                                    }
                                    else
                                        throw ex;
                                }
                            }

                            if( resultHandler != null ) {

                                String newPassword = (String)resultHandler.apply( connection, 1 );

                                if( newPassword == null || newPassword.isEmpty() )
                                    throw new SQLException("[Xxi-Connector] - To log on you must to change the password.");

//                                info[0] = newPassword;

                                final String userName = holderAuth.get().getUserName();

                                holderAuth.set (
                                    new PasswordAuthentication (
                                        userName,
                                        newPassword.toCharArray()
                                    )
                                );

                                resultCode = 1; // признак, что было выполнено смена пароля
                            }
                            else
                                throw new SQLException("[Xxi-Connector]- To log on you must to change the password.");

                        }
                        break;
                        case -1:
                        case -20566:
                        {
                            //info[0] = out_c;
                            msg.set( out_c );
                            resultCode = -1;
                        }
                    }//end switch

                    if( resultCode != -1 && resultHandler != null ) {

                        String m = st.getString(5);

                        if( S.isNotNullOrEmpty(m) )
                            resultHandler.apply( connection, m );
                    }
                }
            }

            return resultCode;
        }
        catch( SQLException ex ) {

            if( !loginXxiOk && ex.getErrorCode() == 1017 )
                throw new SQLException( "[Xxi-Connector] - Error on XXI_LOGON login", ex.getSQLState(), ex.getErrorCode() );

            throw ex;
        }
        catch( Throwable th ) {
            throw new SQLException( "[Zxi-Connector] - error on connect to DB", th );
        }
    }

    private static final String DATABASE_CHARSET8BIT = "AMERICAN_CIS.CL8MSWIN1251";
    private static final Charset JAVA_CHARSET8BIT = Charset.forName ("cp1251");

    /** */ 
    private String loginAndGetRealEncryptedPassword( String jdbcUrl, String login, String pwd, Properties p ) throws SQLException {

logger.debug("getConnection: url {} ... ", jdbcUrl );
        
        try( Connection conn = DriverManager.getConnection( jdbcUrl, p ) ) 
        {

logger.debug("OK." );

logger.debug("prepareCall... " );

            try( CallableStatement st = conn.prepareCall("{? = call XXI.XXI_LOGON.XXI_LOGON(?, ?)}") ) 
            {

logger.debug("OK." );

                st.registerOutParameter(1, Types.INTEGER);
                st.setString(2, login.toUpperCase() + "/" + pwd.toUpperCase() );
                st.registerOutParameter(3, Types.VARCHAR);

logger.debug("execute ... " );
                st.execute();
logger.debug("OK." );

                int resultCode = st.getInt(1);

                switch (resultCode) {
                    case 0:
                    {
                        String realEncryptedPassword = st.getString(3);
                        return realEncryptedPassword;
                    }
                    case 1:
                    {
                        throw new SQLException("[Xxi-Connector] - To log into cABS 'XXI Vek' you must to change the password.");
                    }
                    case -1:
                    {
/*                        
                        String encryptedErrorDesc = st.getString(3);
                        String decryptedErrorDesc = decryptHexString(encryptedErrorDesc);
                        String readableErrorDescr = decodeString    ("Cp1251", "ISO8859-5", decryptedErrorDesc );
                        throw new SQLException( "[Xxi-Connector] - " + readableErrorDescr );
*///////                        
                    }
                    default:
                        throw new SQLException("[Xxi-Connector] - Unknown return value from 'XXI_LOGON': " + resultCode );
                }
            }//end try
        }//end try
    }

    /** */
    private byte[] hexToByte( String hexString ) {
        return DatatypeConverter.parseHexBinary(hexString);
    }

    private static final char[] rDecToHex = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};

    private byte[] char2Byte( char ch ) {
        int ich = (int)ch;
        return hexToByte( "" + rDecToHex[1 + ich/16] + rDecToHex[ 1 + ich % 16 ] );
    }

    /** */
    protected String My_Convert( char[] s ) {
        StringBuilder result = new StringBuilder(s.length);
        int i = 0;
        for (; i < s.length - 1; i++) {
            result.append((char) (s[i] - 1));
        }
        return result.toString();
    }

    protected byte byte_h2d(byte h) {
        if (h > 64 && h < 71) {
            h = (byte) (h - 55);
        } else {
            h = (byte) (h - 48);
        }

        return h;
    }

    protected char char_h2d(char h) {
        if (h > 64 && h < 71) {
            h = (char) (h - 55);
        } else {
            h = (char) (h - 48);
        }

        return h;
    }

    /** */
    private String decryptHexString( String hexString ) {

        if( hexString != null )
        {
            String c_shift = hexString.substring(0, 4);
            int i_shift = Integer.valueOf(c_shift);
            String ctx_key = My_Convert(rDefKey);
            arc4engine ctx = new arc4engine();
            ctx.init(ctx_key.toCharArray());
            for (int i = 0; i < i_shift; i++) {
                ctx.arc4Next();
            }
            int i_len = hexString.length() / 2 - 2;
            char[] cry_str = new char[i_len];
            char[] cr_data = hexString.substring(4, hexString.length()).toCharArray();
            for (int i = 0; i < i_len; i++) {
                char tmpChar = (char) (16 * char_h2d(cr_data[2 * i]) + char_h2d(cr_data[2 * i + 1]));
                cry_str[i] = tmpChar;
            }
            String result = ctx.arc4(cry_str);
            int i = result.indexOf('/');
            String realString = result.substring(i + 1, result.length());

            return realString;
        }

        return null;
    }

    /** */
    private static class arc4engine {

        private int ei = 0;
        private int ej = 0;
        private ArrayList<Character> k = new ArrayList<>();
        private ArrayList<Character> s = new ArrayList<>();

        public arc4engine() {
        }

        public char arc4Next() {
            ei = (ei + 1) % 0x100;
            ej = (ej + s.get(ei)) % 0x100;
            //swap
            char ejVal = s.get(ej);
            char eiVal = s.get(ei);
            s.set(ej, eiVal);
            s.set(ei, ejVal);
            int t = (s.get(ei) + s.get(ej)) % 0x100;
            return s.get(t);
        }

        public void init(char[] key) {
            int kl = key.length;
            int i = 0, j = 0;
            for (i = 0; i < 256; i++) {
                s.add((char) i);
                k.add(key[j]);
                if (++j == kl) {
                    j = 0;
                }
            }
            for (i = 0, j = 0; i < 256; i++) {
                j = (j + s.get(i) + k.get(i)) % 0x100;
                //swap
                char jVal = s.get(j);
                char iVal = s.get(i);
                s.set(j, iVal);
                s.set(i, jVal);
            }
        }

        public String arc4( char[] inStr ) {
            int sl = inStr.length;
            int i = 0;
            char a, b;
            byte[] outStr = new byte[sl];
            for (i = 0; i < sl; i++) {
                a = inStr[i];
                b = arc4Next();
                outStr[i] = (byte) (a ^ b);
            }
            return new String(outStr);
        }
          
        public byte[] arc4b ( byte[] inStr ) 
        {
            byte a, b;
            byte[] outStr = new byte [inStr.length];
            
            for (int i = 0; i < inStr.length; i++) 
            {
                a = inStr [i];
                b = (byte) arc4Next ();
                outStr [i] = (byte) (a ^ b);
            }
            
            return outStr;
        }        
        
    }
//
// test
//
    public static void main ( String[] args ) throws Exception 
    {
        Properties prop = new Properties ();
        prop.setProperty("ignore_check_xxi", "true");
        Class.forName ("oracle.jdbc.driver.OracleDriver");
        final PasswordAuthentication gan = XxiConnector.login("jdbc:oracle:thin:@koral:1521:dev8i", "GAN", "JAVA-OMG", prop, null);
        System.out.println("login="+gan.getUserName());
    }
//
//
//    
}
