package ru.inversion.db.session.xxi;

import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.security.PasswordMaker;

import java.net.PasswordAuthentication;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.function.BiFunction;

import static ru.inversion.db.session.AbstractDataSourceCfgBuilder.V$SESSION_PROGRAM;
import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.*;
import static ru.inversion.utils.security.PasswordMaker.CaseMode.Mixed;

/** */
public class PGPseudoConnector extends ConnectorBase {
    /** */
    private static class UserInfo {

        final private PasswordAuthentication auth;
        /** */
        private LocalDate pseudoPasswordDate;
        /** */
        private String serverIp;
        /** */
        private LocalDateTime serverTime;
        /** */
        private char conType;
        /** */
        private char[] pseudoPassword;

        /** */
        public UserInfo( String userName, String pseudoPassword ) {
            auth = new PasswordAuthentication( userName, pseudoPassword.toCharArray() );
        }

        /** */
        public UserInfo( PasswordAuthentication auth ) {
            this.auth = auth;
        }

        /** */
        public String userName( ) {return auth.getUserName();}
        /** */
        public char[] password() {return auth.getPassword();}
        /** */
        public LocalDate pseudoPasswordTime() { return pseudoPasswordDate;}
        public void pseudoPasswordTime( LocalDate v ) { pseudoPasswordDate = v;}

        /** */
        public LocalDateTime serverTime() { return serverTime;}
        public void serverTime( LocalDateTime lt ) { serverTime = lt;}

        /** */
        public String serverIp( ) { return serverIp; }
        public void serverIp( String serverIp ) { this.serverIp = serverIp; }

        /** */
        public char conType( ) { return conType; }
        public void conType( char conType ) { this.conType = conType; }

        public char[] pseudoPassword() {
            return pseudoPassword;
        }
        public void pseudoPassword( char[] v ) {
            this.pseudoPassword = v;
        }
    }

    /** */
    private PGPseudoConnector()
    { }

    /** */
    private int call_chekLogin( Connection c, UserInfo userInfo )
    {

        try( Statement st = c.createStatement() ) {
            try( ResultSet rs = st.executeQuery("select host( inet_server_addr())::varchar ip, clock_timestamp() ts") ) {
                if( !rs.next() )
                    throw new SQLException("Bad POSTGRESQL server");
                userInfo.serverIp  ( rs.getString(1) );
                userInfo.serverTime( TypeConverter.convert( rs.getObject(2), LocalDateTime.class ) );
            }
        }
        catch (SQLException e) {
            throw XXIConnectorException.fromSql( e, U.toMap( "login", userInfo.userName(), "op", "read_server_ip_time") );
        }
        catch (Throwable th) {
            throw XXIConnectorException.of( INTERNAL_ERROR, "Failed to read server ip/time", th, U.toMap("login", userInfo.userName(), "op", "read_server_ip_time"));
        }

        try ( CallableStatement cs = c.prepareCall( "{call xxi_pseudo.check_Login(?,?,?,?,?,?,?)}") ) {
            int i = 1;
            cs.setString( i++, userInfo.userName() );
            cs.setString( i++, String.valueOf( encrypt4Send( userInfo.password(), userInfo.serverIp, userInfo.serverTime.toLocalTime() ) ) );

            cs.registerOutParameter( i++, Types.VARCHAR );
            cs.registerOutParameter( i++, Types.VARCHAR );
            cs.registerOutParameter( i++, Types.DATE    );
            cs.registerOutParameter( i++, Types.NUMERIC );
            cs.registerOutParameter( i++, Types.VARCHAR );

            cs.execute();

            int result = TypeConverter.convert( cs.getObject(6), Integer.class );

            if( result == -1 )
            {
                final String result_Info = cs.getString(7);

                if( "QLIC".equals(result_Info) )
                    throw XXIConnectorException.of( LICENCE_ERROR,  "Превышено число лицензий ЦАБС \"БАНК 21 ВЕК\"", null, U.toMap("login", userInfo.userName(), "pg_reason", result_Info) );
                if( "XUNE".equals(result_Info) )
                    throw XXIConnectorException.of( USER_NOT_FOUND, "User not found", null, U.toMap("login", userInfo.userName(), "pg_reason", result_Info) );
                if( "NXUP".equals(result_Info))
                    throw XXIConnectorException.of( NO_XXI_PASSWORD,"No password set for user", null, U.toMap("login", userInfo.userName(), "pg_reason", result_Info) );

                // Неизвестный reason от процедуры
                throw XXIConnectorException.of( AUTH_FAILED, result_Info, null, U.toMap("login", userInfo.userName() ) );
            }

            Object o = cs.getObject(3);

            if( !S.isString(o) || o.toString().isEmpty() )
                throw new SQLException( Tags.PRODUCT_LABEL + "Не корректный возврат для параметра 'conType' в xxi_pseudo.chek_Login" );

            userInfo.conType( cs.getString(3).charAt(0) );

            if( userInfo.conType == 'F' )
            {
                String pswd = cs.getString(4);
                Object dswd = cs.getObject(5);
                userInfo.pseudoPassword( pswd == null ? null : pswd.toCharArray() );
                userInfo.pseudoPasswordTime( dswd == null ? null : TypeConverter.convert( dswd, LocalDate.class ));
            }
            else
            {
                userInfo.pseudoPassword( null );
                userInfo.pseudoPasswordTime( null );
            }

            return result;
        }
        catch( SQLException e ) {
            throw XXIConnectorException.fromSql( e, U.toMap( "login", userInfo.userName(), "op", "xxi_pseudo.check_Login" ));
        }
    }

    /** */
    private char[] call_changePassword( Connection c, UserInfo userInfo )
    {
        char[] pseudoPassword;
        int l = 30;

        do {

            pseudoPassword = PasswordMaker.NEW().length(l).useDigits().caseMode(Mixed).make();

            try (
                CallableStatement cs = c.prepareCall("{call xxi_pseudo.change_Password(?,?,?,?,?)}")
            ) {

                cs.setString( 1, userInfo.userName() );
                cs.setString( 2, encrypt4Send ( pseudoPassword, userInfo.serverIp, userInfo.serverTime.toLocalTime() ) );
                cs.setString( 3, encrypt4Store( pseudoPassword ) );

                cs.registerOutParameter( 4, Types.NUMERIC );
                cs.registerOutParameter( 5, Types.VARCHAR );

                cs.execute() ;

                int result = TypeConverter.convert( cs.getObject(4), Integer.class );

                if( result == -1 )
                {
                    final String result_Info = cs.getString(5);

                    if ("28P01".equals(result_Info) || "XUNE".equals(result_Info))
                        throw XXIConnectorException.of( USER_NOT_FOUND, "User not found", null, U.toMap("login", userInfo.userName(), "pg_reason", result_Info) );

                    throw XXIConnectorException.of( INTERNAL_ERROR, "xxi_pseudo.change_Password failed", null, U.toMap("login", userInfo.userName(), "pg_reason", result_Info) );
                }

                userInfo.pseudoPassword( pseudoPassword );

            }
            catch( SQLException e ) {

                pseudoPassword = null;

                if( "28P18".equals( e.getSQLState() ) )
                    l -= 5;
                else if( "28P19".equals( e.getSQLState() ) )
                    l += 3;
                else
                    throw XXIConnectorException.fromSql(e, U.toMap( "login", userInfo.userName(), "op", "xxi_pseudo.change_Password" ));
            }
        } while( pseudoPassword == null );

        return pseudoPassword;
    }

    /** */
    public static PasswordAuthentication login (
        String jdbcUrl,
        String login,
        String password,
        Properties properties, // base
        BiFunction< Connection, Object, Object > resultHandler
    )
        throws SQLException
    {
        if( properties.getProperty( "ApplicationName") == null )
            properties.put("ApplicationName", V$SESSION_PROGRAM );

        final boolean useToken = "true".equals( properties.getProperty("ru.inversion.use_token", "false") );

        if( useToken )// "Логин через токен в данный момент не поддерживается!"
            throw XXIConnectorException.of( INTERNAL_ERROR, "Token login is not supported", null, U.toMap( "login", login, "op", "token_login" ) );

        final Properties techProperties = new Properties( properties );
        {
            final PasswordAuthentication techUserAuth = PGPseudoConnector.getTechUserAuth();

            techProperties.setProperty( "user",     techUserAuth.getUserName() );
            techProperties.setProperty( "password", String.valueOf( techUserAuth.getPassword() ) );
        }

        final PGPseudoConnector connector = new PGPseudoConnector();

        final PasswordAuthentication authentication;

        try( final Connection techConnection = DriverManager.getConnection( jdbcUrl, techProperties ) ) {
            techConnection.setAutoCommit(true);

            check4XxiUpgrade(techConnection, techProperties);

            UserInfo userInfo = new UserInfo(login, password);

            final int result = connector.call_chekLogin(techConnection, userInfo);

            if (result == 1) {
                // Проверка на правильность переданного пароля,
                // чтоб показать диалог смены пароля
                {
                    Properties tryProp = new Properties(properties);
                    tryProp.setProperty("password", password);
                    tryProp.setProperty("user", login);

                    try (Connection c = DriverManager.getConnection(jdbcUrl, tryProp)) {

                    } catch (SQLException e) {
                        if ("28P01".equals(e.getSQLState())) {
                            // если пароль устарел, то не ошибка
                            // просто меняем его
                        } else
                            throw e;
                    }
                }

                if( resultHandler != null )
                {
                    String newPassword = (String) resultHandler.apply(techConnection, 1);

                    if( S.isNotNullOrEmpty(newPassword) )
                        throw XXIConnectorException.of( PASSWORD_CHANGE_REQUIRED, "Password change required", null, U.toMap("login", login) );

                    authentication = new PasswordAuthentication( login, newPassword.toCharArray() );
                }
                else
                    throw XXIConnectorException.of(PASSWORD_CHANGE_REQUIRED, "Password change required", null, U.toMap("login", login));

            }//result 1
            // если псевдо-соединение
            else if( userInfo.conType() == 'F' )
            {
                final LocalDate d = userInfo.pseudoPasswordTime();

                // Если пароль генерился сегодня
                if( userInfo.pseudoPassword != null && d != null && d.equals( userInfo.serverTime().toLocalDate() ) )
                {
                    authentication = new PasswordAuthentication (
                        login,
                        decrypt4Store( String.valueOf( userInfo.pseudoPassword() ) )
                    );
                }
                else
                {
                    authentication = new PasswordAuthentication( login, connector.call_changePassword( techConnection, userInfo ) );
                }
            }
            else
                authentication = new PasswordAuthentication( login, password.toCharArray() );


            techProperties.setProperty( "user",     authentication.getUserName() );
            techProperties.setProperty( "password", String.valueOf(authentication.getPassword()) );

            try( Connection c = DriverManager.getConnection( jdbcUrl, techProperties )) {

//            if( useToken )
//                check4TokenData( c, properties );

                if (result == 2 && resultHandler != null) {

                    if( (Boolean) resultHandler.apply(c, 2) ) {
                        ;//return String.valueOf( realAuthentication.getPassword() );
                    } else
                        throw XXIConnectorException.of(AUTH_FAILED, "No PIN - no entry", null, U.toMap("login", login));
                }
            } catch (Exception ex) {
                // Признак, что была смена пароля
                // Не можем соединиться т.к. скорее всего был вход через псевдосоединение
                if (result == 1)
                    throw XXIConnectorException.of( PASSWORD_CHANGE_REQUIRED, "To apply changes you must login again", null, U.toMap("login", login, "op", "relogin_required_after_password_change"));
                throw ex;
            }

            return authentication;
        }
        catch( SQLException e ) {
            throw XXIConnectorException.fromSql(e, U.toMap( "login", login, "jdbcUrl", jdbcUrl, "op", "login" ));
        }
    }

    // PGPseudoConnector
    public static PasswordAuthentication serverSideLogin( Connection techConnection, PasswordAuthentication auth, Properties properties )
    {
        if( properties == null )
            properties = new Properties();

        if( properties.getProperty("ApplicationName") == null )
            properties.put("ApplicationName", V$SESSION_PROGRAM + ".ServerSide");

        final boolean useToken = "true".equals(properties.getProperty("ru.inversion.use_token", "false"));
        if (useToken)
            throw XXIConnectorException.of( INTERNAL_ERROR, "Token login is not supported", null, U.toMap("login", auth.getUserName(), "op", "token_login"));

        final PGPseudoConnector connector = new PGPseudoConnector();

        try {

            check4XxiUpgrade( techConnection, properties );

            UserInfo userInfo = new UserInfo(auth);

            int result = connector.call_chekLogin(techConnection, userInfo);

            // result == 1 => пароль просрочен/нужна смена
            if( result == 1 )
                throw XXIConnectorException.of( PASSWORD_CHANGE_REQUIRED, "Password change required", null, U.toMap("login", auth.getUserName(), "op", "serverSideLogin"));

            // conType == 'F' => псевдо-режим
            if( userInfo.conType() == 'F' )
            {
                LocalDate d = userInfo.pseudoPasswordTime();

                // "старый больше суток" == "не за сегодня"
                if( userInfo.pseudoPassword() != null
                        && d != null
                            && d.equals(userInfo.serverTime().toLocalDate() ) )
                {
                    char[] realPwd = decrypt4Store(String.valueOf(userInfo.pseudoPassword()));
                    return new PasswordAuthentication(auth.getUserName(), realPwd);
                }
                else
                {
                    char[] newPwd = connector.call_changePassword(techConnection, userInfo);
                    return new PasswordAuthentication(auth.getUserName(), newPwd);
                }
            }

            try( Connection c = DriverManager.getConnection( techConnection.getMetaData().getURL(), auth.getUserName(), String.valueOf( auth.getPassword() ) )) {
                if( result == 2)
                    throw XXIConnectorException.of(AUTH_FAILED, "Для входа требуется подтверждение через PIN! В данном режиме логина не поддерживается. См. настройки входа в систему для пользователя", null, U.toMap("login", auth.getUserName()) );
            }

            // обычный режим — клиент и так знает свой пароль, но для единообразия возвращаем его же
            return auth;

        } catch (SQLException e) {
            throw XXIConnectorException.fromSql( e, U.toMap("login", auth.getUserName(), "mode", "serverSide", "op", "serverLogin"));
        }
    }
}
