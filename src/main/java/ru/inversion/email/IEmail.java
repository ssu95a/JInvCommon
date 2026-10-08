package ru.inversion.email;

import javax.mail.internet.InternetAddress;
import java.util.List;

/**
 *  Модуль отправки сообщений через SMTP
 *
 *  Интерфейс описывающий одно e-mail письмо
 *
 *  IPojo. Данные одного письма
 *
 *  @author  Sulimoff
 *  @version changed 09.04.2020/created 09.04.2020
 */
public interface IEmail {

    /** Виды получателей
     *  (пока не используется)
     */
    enum RecipientTypeEnum {
        TO, CC, BCC
    }

    /** Одна часть письма, может быть content - содержимое, либо вложение */
    interface IItem<T> {

        /** Наименование */
        String getName( );

        /** Тип данных */
        String getMimeType( );

        /** Данные */
        T getData( );
    }

    /** Адрес с которого происходит отправка письма */
    InternetAddress getFrom( );

    /** Список получателей  */
    InternetAddress[] getRecipients( RecipientTypeEnum recipientType );

    /** Тема письма */
    String getSubject( );

    /** Тело письма, м,б. несколько для много-частевых писем */
    List<IItem<String>> getContentList( );

    /** Список вложений */
    List<IItem<Object>> getAttachList( );
}
