package ru.inversion.utils.security;

import java.net.PasswordAuthentication;
import java.util.Arrays;

public class PasswordAuthenticationCleaner implements AutoCloseable {

    final private PasswordAuthentication auth;

    public PasswordAuthenticationCleaner(PasswordAuthentication auth) {
        this.auth = auth;
    }

    @Override
    public void close() {
        if( auth != null && auth.getPassword() != null )
            Arrays.fill( auth.getPassword(), '\0');
    }
}
