package com.lucchetta.dataprovider.random;

import com.lucchetta.core.port.InviteCodeGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Ten characters from an alphabet without look-alikes (no 0/O, 1/I/L): about 50 bits, easy to
 * type from a message.
 */
@Component
public class SecureRandomInviteCodeGenerator implements InviteCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
