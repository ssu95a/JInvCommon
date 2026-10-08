package ru.inversion.db.entity;

/**
 *
 * @author antonovdi
 */
public enum ContentTypeEnum {

    // date & time zone
    DATE,      // dd.MM.yyyy
    TIME,           // HH:mm:ss
    TIME_NO_SECONDS,
    DATE_TIME,// dd.MM.yyyy hh:mm
    TIME_DATE,// hh:mm dd.MM.yyyy
    DATE_TIME_DETAIL, // dd.MM.yyyy hh:mm:ss
    TIME_DATE_DETAIL, // hh:mm:ss dd.MM.yyyy
    
    
    // number
    STRING_VALUE,
    
    // decimal zone
    MONEY,
    PERCENT,
    
    // string zone
    INTEGER_VALUE,
    CURRENCY,
    PHONE,
    EMAIL,
    URL,
    XML,
    HTML,
    ICON_CODE
}
