package ru.inversion.email;

import ru.inversion.email.impl.EmailBuilderImpl;
import ru.inversion.email.impl.EmailServiceImpl;
import ru.inversion.utils.U;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 *  Модуль отправки сообщений через SMTP
 *
 *  Служба e-mail.
 *
 *  @author  Sulimoff
 *  @version changed 09.04.2020/created 09.04.2020
 */
public class EmailService {

    //
    static final public String SMTP_HOST         = "ri.mail.smtp.host";
    //
    static final public String SMTP_PORT         = "ri.mail.smtp.port";
    //
    static final public String DEFAULT_FROM_ADDR = "ri.mail.default_from_addr";
    //
    static final public String DEFAULT_FROM_NAME = "ri.mail.default_from_name";
    //
    static final public String SMTP_DEBUG        = "ri.mail.smtp.debug";
    //
    static final public String SMTP_LOGIN        = "ri.mail.smtp.login";
    //
    static final public String SMTP_PASSWORD     = "ri.mail.smtp.password";

    /** Поставщик параметров службы */
    static private Function<String,Object> defaultSettingsSupplier = null;

    /**
     * Установка параметров по умолчанию
     *
     * @return Предыдыущие параметры по умолчанию, установленные до текущего вызова.
     * */
    public static Function<String,Object> setDefaultSettingsSupplier( Function<String,Object> settingsSupplier ) {
        Function<String,Object> old = defaultSettingsSupplier;
        defaultSettingsSupplier = settingsSupplier;
        return old;
    }

    /** Получение параметров по умолчания */
    public static Function<String,Object> getDefaultSettingsSupplier( ) {
        return defaultSettingsSupplier;
    }

    /** Создает новый построитель почтового сообщения */
    public static IEmailBuilder newBuilder( )
    {
        return new EmailBuilderImpl( getDefaultSettingsSupplier() );
    }

    /** Создает новый построитель почтового сообщения */
    public static IEmailBuilder newBuilder( Function<String,Object> settingsSupplier )
    {
        return new EmailBuilderImpl( settingsSupplier );
    }

    /**
     *  Отправляем сообщение используя настройки по умолчанию
     *
     *  Уведомляет вызывающий код о ходе отправки через слушатель listener
     */
    public static void send( IEmail email, BiConsumer<Integer,String> listener ) throws EmailException
    {
        EmailServiceImpl.instance().send( email, defaultSettingsSupplier, listener );
    }

    /**
     *  Отправляет сообщение используя settingsSupplier
     *
     *  Уведомляет вызывающий код о ходе отправки через слушатель listener
     */
    public static void send( IEmail email, Function<String,Object> settingsSupplier, BiConsumer<Integer,String> listener ) throws EmailException
    {
        EmailServiceImpl.instance().send( email, U.nvl( settingsSupplier, getDefaultSettingsSupplier() ), listener );
    }
}
