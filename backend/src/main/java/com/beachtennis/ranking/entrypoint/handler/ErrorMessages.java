package com.beachtennis.ranking.entrypoint.handler;

import java.util.Map;

/**
 * User-facing (pt-BR) messages for business error codes.
 */
final class ErrorMessages {

    static final String INVALID_REQUEST = "INVALID_REQUEST";

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry(INVALID_REQUEST, "Os dados enviados são inválidos."),
            Map.entry("FORBIDDEN", "Você não tem permissão para realizar esta operação."),
            Map.entry("INVALID_DISPLAY_NAME", "O nome de exibição deve ter entre 2 e 40 caracteres."),
            Map.entry("INVALID_SEASON_NAME", "O nome da temporada deve ter entre 2 e 60 caracteres."),
            Map.entry("INVALID_SEASON_PERIOD", "A data de término deve ser igual ou posterior à data de início."),
            Map.entry("SEASON_END_IN_PAST", "A data de término não pode ser anterior a hoje."),
            Map.entry("SEASON_OVERLAP", "O período informado se sobrepõe ao de outra temporada."),
            Map.entry("SEASON_CLOSED", "Temporadas encerradas não podem ser alteradas."),
            Map.entry("SEASON_START_LOCKED", "A data de início de uma temporada em andamento não pode ser alterada."),
            Map.entry("SEASON_NOT_FOUND", "Temporada não encontrada."));

    private static final String FALLBACK = "Não foi possível concluir a operação.";

    private ErrorMessages() {
    }

    static String forCode(String code) {
        return MESSAGES.getOrDefault(code, FALLBACK);
    }
}
