package com.beachtennis.ranking.entrypoint.handler;

import java.util.Map;

/**
 * User-facing (pt-BR) messages for business error codes.
 */
final class ErrorMessages {

    static final String INVALID_REQUEST = "INVALID_REQUEST";

    private static final Map<String, String> MESSAGES = Map.of(
            INVALID_REQUEST, "Os dados enviados são inválidos.",
            "INVALID_DISPLAY_NAME", "O nome de exibição deve ter entre 2 e 40 caracteres.");

    private static final String FALLBACK = "Não foi possível concluir a operação.";

    private ErrorMessages() {
    }

    static String forCode(String code) {
        return MESSAGES.getOrDefault(code, FALLBACK);
    }
}
