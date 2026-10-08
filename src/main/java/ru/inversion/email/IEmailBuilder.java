package ru.inversion.email;

import javax.mail.internet.InternetAddress;
import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 *  Модуль отправки сообщений через SMTP
 *
 *  Интерфейс builder писем
 *
 *  @author  Sulimoff
 *  @version changed 09.04.2020/created 09.04.2020
 */
public interface IEmailBuilder {

    /** Тема письма */
    IEmailBuilder subject( String subject );

    /** Адрес отправителя */
    IEmailBuilder from( String fromAddress );

    /** Адрес отправителя с именем отправителя */
    IEmailBuilder from( String fromAddress, String fromName );

    /** Адрес отправителя */
    IEmailBuilder from( InternetAddress fromAddress );

    /** Адрес получателя */
    IEmailBuilder recipient ( String recipientAddress );

    /** Адрес получателя */
    IEmailBuilder recipient ( InternetAddress recipientAddress );

    /** Адреса получателей */
    IEmailBuilder recipients( Iterable<String> recipients );

    /** Адреса получателей */
    IEmailBuilder recipients( Supplier<Iterable<String>> recipientsFactory );

    /** Содержимое письма */
    IEmailBuilder content( String text );

    /** Содержимое письма */
    IEmailBuilder content( Object data, String mimeType );

    /** Вложение  */
    IEmailBuilder attach ( String name, Object data, String mimeType );

    /** Вложение  */
    IEmailBuilder attach ( File file );

    /** Вложение  */
    IEmailBuilder attach ( String name, File file );

    /** Создает письмо  */
    IEmail build( ) throws EmailException;

    /** Создает и отправляет письмо  */
    void send( ) throws EmailException;

    /** Создает и отправляет письмо с уведомлением процесса отправки  */
    void send( BiConsumer<Integer,String> listener ) throws EmailException;
}
