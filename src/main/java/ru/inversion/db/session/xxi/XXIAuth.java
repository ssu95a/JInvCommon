package ru.inversion.db.session.xxi;

import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;

public class XXIAuth {

    private static final byte[] DATA = {49, 22, 31, 58, 17, 28, 39, 1, 11, 28, 23, 64, 26, 31, 16, 2, 0, 10, 95, 24};

    static PasswordAuthentication getUserInfo( byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        byte[] decryptedData = new byte[DATA.length];
        for (int i = 0; i < DATA.length; i++) {
            decryptedData[i] = (byte) (DATA[i] ^ key[i % key.length]);
        }

        String authString = new String(decryptedData, StandardCharsets.UTF_8);
        String[] parts = authString.split("/", 2);
        
        if (parts.length == 2) {
            return new PasswordAuthentication(parts[0], parts[1].toCharArray());
        } else {
            throw new RuntimeException("Invalid authentication data format");
        }
    }

    static byte[] getMasterKey( ) {
        return new byte[] {120,88,105,95,109,97,115,116,101,82,95,111,107,75,101,121};
    }
}
