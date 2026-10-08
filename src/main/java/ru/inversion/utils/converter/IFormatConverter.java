/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.inversion.utils.converter;

/**
 *
 * @author antonovdi
 */
public interface IFormatConverter<S> extends IConverter<S, String> {

    /**
     * S->String
     */
    String to(S value, String formatMask );

    /**
     * String->S
     */
    S from( String value, String formatMask );
}
