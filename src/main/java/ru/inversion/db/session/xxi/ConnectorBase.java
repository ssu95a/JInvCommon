package ru.inversion.db.session.xxi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Properties;

import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.*;

/** */
public abstract class ConnectorBase extends XXIAuth {

    protected static final byte[] KEY = {105, 110, 118, 101, 114, 115, 105, 111, 110, 95, 99, 111};

    protected static Logger logger = LoggerFactory.getLogger("ru.inversion.xxi.connector");
    /** */
    public static PasswordAuthentication getTechUserAuth( )
    {
        return getUserInfo(KEY);
    }

    /** */
    static String yand_e( byte[] d, byte[] k )
    {
        byte[] b = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            b[i] = (byte) (d[i] ^ k[i % k.length]);
        }
        return Base64.getEncoder().encodeToString(b);
    }

    /** */
    static char[] yand_d( String s, byte[] k )
    {
        byte[] d = Base64.getDecoder().decode(s);

        byte[] b = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            b[i] = (byte) (d[i] ^ k[i % k.length]);
        }
        return new String(b, StandardCharsets.UTF_8).toCharArray();
    }

    /** Шифрование для хранения */
    static String encrypt4Store( char[] p )
    {
        try {
            // Генерируем IV
            byte[] iv = new byte[12];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Создаем ключ из мастер-ключа
            SecretKey secretKey = new SecretKeySpec( getMasterKey(), "AES");

            // Шифруем
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encrypted = cipher.doFinal( TypeConverter.convert(p, byte[].class ));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString( combined );

        } catch( Exception e) {

            //"Ошибка при формировании пароля."
            throw XXIConnectorException.of( ENCRYPTION_ERROR, "Encryption error", e, U.toMap("op", "encrypt4Store") );
        }
    }

    /** Дешифрование строки из хранения */
    public static char[] decrypt4Store( String p) {

        try {

            byte[] combined = Base64.getDecoder().decode(p);

            // Проверяем минимальную длину
            if( combined.length < 12)
                throw new IllegalArgumentException("encrypted_data.too_short");

            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];

            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);

            // Создаем ключ из мастер-ключа
            SecretKey secretKey = new SecretKeySpec( getMasterKey(), "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec( 128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] db = cipher.doFinal(encrypted);

            return TypeConverter.convert( db, char[].class );
        } catch (Exception e) {
            throw XXIConnectorException.of( DECRYPTION_ERROR, "Decryption error", e, U.toMap( "op", "decrypt4Store") );
        }
    }

    /** Щифрование строки для передачи по сети */
    static String encrypt4Send( char[] s, String k1, LocalTime t ) {
        // Округляем время до 5 минут (вправо)
        int m = ( (t.getMinute() + 4) / 5) * 5; // Округление вправо до 5 минут
        int h = t.getHour();
        if (m == 60) {
            m = 0;
            h = (h + 1) % 24; //h++
        }
        final String k = String.format("%s|%02d%02d", k1, h, m );

        // XOR шифрование
        final byte[] keyBytes = k.getBytes( java.nio.charset.StandardCharsets.UTF_8 );

        return yand_e( TypeConverter.convert( s, byte[].class ), keyBytes );
    }

    /** */
    static void check4XxiUpgrade( Connection c, Properties p ) throws SQLException {

        if( "true".equals( p.getProperty("ru.inversion.check_xxi_upgrade_disable", "false") ) )
            return;

        boolean upgradeEnable = false;

        try( final PreparedStatement ps = c.prepareStatement (
                "select OBJECT_ID from all_objects where owner = 'PUBLIC' and object_name = 'XXI_UPGRADE' and OBJECT_TYPE = 'SYNONYM'") ) {

            try( final ResultSet rs = ps.executeQuery() ) {
                upgradeEnable = rs.next();
            }
        }
        catch( Throwable th ) {
            logger.error( Tags.PRODUCT_LABEL + "Error on check XII upgrade", th );
        }

        if( upgradeEnable ) {
            throw XXIConnectorException.of( TECHNICAL_BREAK, "Technical break" );
        }
    }
}
