package com.lucchetta.entrypoint.handler;

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
            Map.entry("INVALID_FULL_NAME", "Informe o nome completo (nome e sobrenome), com 3 a 100 caracteres."),
            Map.entry("INVALID_SEASON_NAME", "O nome da temporada deve ter entre 2 e 60 caracteres."),
            Map.entry("INVALID_SEASON_PERIOD", "A data de término deve ser igual ou posterior à data de início."),
            Map.entry("SEASON_END_IN_PAST", "A data de término não pode ser anterior a hoje."),
            Map.entry("SEASON_OVERLAP", "O período informado se sobrepõe ao de outra temporada."),
            Map.entry("SEASON_CLOSED", "Temporadas encerradas não podem ser alteradas."),
            Map.entry("SEASON_START_LOCKED", "A data de início de uma temporada em andamento não pode ser alterada."),
            Map.entry("INVALID_SEASON_PLAYING_DAYS", "Escolha pelo menos um dia da semana para a temporada."),
            Map.entry("INVALID_PLACEMENT_POINTS",
                    "Os pontos por colocação devem ficar entre 0 e 1000, sem que uma colocação pior valha mais."),
            Map.entry("PLACEMENT_POINTS_NOT_APPLICABLE", "Pontos por colocação só valem para o torneio do dia."),
            Map.entry("SEASON_RANKING_LOCKED",
                    "O tipo de ranking e os pontos só podem mudar antes de a temporada começar."),
            Map.entry("SEASON_NOT_FOUND", "Temporada não encontrada."),
            Map.entry("INVALID_SCORE", "Placar inválido para o formato escolhido."),
            Map.entry("INVALID_TIEBREAK",
                    "Placar do tie-break inválido: informe-o apenas em 7–6 ou 5–4, com vitória da dupla vencedora."),
            Map.entry("INVALID_PARTICIPANTS", "Informe dois participantes em cada dupla."),
            Map.entry("DUPLICATE_PARTICIPANT", "A mesma pessoa não pode ocupar duas posições na partida."),
            Map.entry("INVALID_GUEST_NAME", "O nome do convidado deve ter no máximo 40 caracteres."),
            Map.entry("UNKNOWN_PLAYER", "Um dos jogadores informados não está cadastrado."),
            Map.entry("PLAYER_NOT_LEAGUE_MEMBER",
                    "Um dos jogadores informados não é membro desta liga. Registre-o como convidado."),
            Map.entry("INVALID_MATCH_DATE", "Informe a data e a hora da partida."),
            Map.entry("MATCH_IN_FUTURE", "A data da partida não pode estar no futuro."),
            Map.entry("MATCH_ID_CONFLICT", "Já existe outra partida registrada com este identificador."),
            Map.entry("POSSIBLE_DUPLICATE_MATCH",
                    "Já existe uma partida com as mesmas duplas neste dia. Confirme se é uma nova partida."),
            Map.entry("MATCH_NOT_FOUND", "Partida não encontrada."),
            Map.entry("PLAYER_NOT_FOUND", "Jogador não encontrado."),
            Map.entry("MATCH_VOIDED", "Partidas anuladas não podem ser corrigidas. Reverta a anulação primeiro."),
            Map.entry("MATCH_ALREADY_VOIDED", "Esta partida já está anulada."),
            Map.entry("MATCH_NOT_VOIDED", "Esta partida não está anulada."),
            Map.entry("MATCH_MODIFIED", "A partida foi alterada por outra operação. Atualize e tente novamente."),
            Map.entry("REASON_REQUIRED", "Informe a justificativa."),
            Map.entry("REASON_TOO_LONG", "A justificativa deve ter no máximo 500 caracteres."),
            Map.entry("NOTIFICATION_NOT_FOUND", "Notificação não encontrada."),
            Map.entry("NOTIFICATION_IDS_INVALID", "Informe de 1 a 100 notificações para excluir."),
            Map.entry("LEAGUE_NOT_FOUND", "Liga não encontrada."),
            Map.entry("INVALID_LEAGUE_NAME", "O nome da liga deve ter entre 3 e 60 caracteres."),
            Map.entry("INVALID_LEAGUE_DESCRIPTION", "A descrição da liga deve ter no máximo 500 caracteres."),
            Map.entry("INVALID_LEAGUE_VISIBILITY", "Informe se a liga é pública ou privada."),
            Map.entry("INVALID_LEAGUE_SCORE_ENTRY",
                    "Informe quem lança os placares: qualquer membro ou só os administradores."),
            Map.entry("SCORE_ENTRY_ADMINS_ONLY", "Nesta liga, só os administradores lançam os placares."),
            Map.entry("STAGE_NOT_FOUND", "Etapa não encontrada."),
            Map.entry("STAGE_IN_FUTURE", "A etapa não pode ser aberta para um dia que ainda não chegou."),
            Map.entry("STAGE_OUTSIDE_TOURNAMENT_SEASON",
                    "Não há uma temporada de torneio do dia em andamento nessa data."),
            Map.entry("STAGE_NOT_A_PLAYING_DAY", "Esse dia da semana não é um dia de jogo da temporada."),
            Map.entry("STAGE_ALREADY_EXISTS", "Já existe uma etapa nesse dia."),
            Map.entry("STAGE_MODIFIED", "A etapa foi alterada por outra operação. Atualize e tente novamente."),
            Map.entry("INVALID_STAGE_FORMAT", "Escolha o formato dos jogos da etapa: normal ou curto."),
            Map.entry("INVALID_STAGE_PLAYER_COUNT", "A etapa precisa de 8 a 64 jogadores presentes."),
            Map.entry("STAGE_ODD_PLAYER_COUNT",
                    "O número de jogadores precisa ser par: inclua um convidado ou retire alguém."),
            Map.entry("DUPLICATE_STAGE_PLAYER", "Um jogador foi incluído mais de uma vez na etapa."),
            Map.entry("INVALID_STAGE_GUEST_NAME", "Informe o nome do convidado, com até 40 caracteres."),
            Map.entry("STAGE_STARTED",
                    "A etapa já tem placares: o sorteio não pode mais ser refeito nem a etapa excluída."),
            Map.entry("STAGE_FIXTURE_NOT_FOUND", "Jogo não encontrado nesta etapa."),
            Map.entry("STAGE_FIXTURE_NOT_SCORED", "Este jogo ainda não tem placar."),
            Map.entry("STAGE_SCORE_ALREADY_ENTERED",
                    "Este jogo já tem placar. Só os administradores podem corrigi-lo."),
            Map.entry("STAGE_GROUPS_CLOSED",
                    "A fase de grupos está encerrada. Reabra os grupos para corrigir este placar."),
            Map.entry("STAGE_GROUPS_NOT_CLOSED", "A fase de grupos ainda não foi encerrada."),
            Map.entry("STAGE_KNOCKOUT_STARTED",
                    "As eliminatórias já têm placares. Remova esses placares antes de reabrir os grupos."),
            Map.entry("STAGE_LATER_ROUND_SCORED",
                    "A rodada seguinte já tem placares. Remova esses placares antes de alterar este jogo."),
            Map.entry("STAGE_MATCH", "Partidas do torneio do dia são corrigidas ou removidas pela etapa."),
            Map.entry("INVALID_LEAGUE_ROLE", "Informe o papel do membro: administrador ou membro."),
            Map.entry("LEAGUE_NAME_TAKEN", "Já existe uma liga com este nome."),
            Map.entry("LEAGUE_LIMIT_REACHED",
                    "Você atingiu o limite de 5 ligas criadas. Arquive uma delas para criar outra."),
            Map.entry("LEAGUE_ARCHIVED", "Esta liga está arquivada e não pode ser alterada."),
            Map.entry("LEAGUE_NOT_ARCHIVED", "Esta liga não está arquivada."),
            Map.entry("LEAGUE_MODIFIED", "A liga foi alterada por outra operação. Atualize e tente novamente."),
            Map.entry("LEAGUE_FULL", "Liga cheia: esta liga atingiu o limite de 50 membros."),
            Map.entry("LEAGUE_PRIVATE", "Esta liga é privada. Solicite a entrada ou use um convite."),
            Map.entry("LEAGUE_MEMBER_NOT_FOUND", "Membro não encontrado nesta liga."),
            Map.entry("LAST_LEAGUE_ADMIN", "A liga precisa ter ao menos um administrador."),
            Map.entry("ALREADY_LEAGUE_MEMBER", "Você já é membro desta liga."),
            Map.entry("LEAGUE_PUBLIC", "Esta liga é pública: entre diretamente, sem pedido."),
            Map.entry("JOIN_REQUEST_NOT_FOUND", "Pedido de entrada não encontrado."),
            Map.entry("JOIN_REQUEST_NOT_PENDING", "Este pedido de entrada já foi respondido ou cancelado."),
            Map.entry("INVITATION_NOT_FOUND", "Convite não encontrado."),
            Map.entry("INVITATION_NOT_PENDING", "Este convite já foi respondido ou cancelado."),
            Map.entry("INVALID_EMAIL", "Informe um e-mail válido."),
            Map.entry("INVITE_CODE_INVALID", "Código de convite inválido ou substituído por um novo."),
            Map.entry("INVALID_DEVICE", "Dados do dispositivo inválidos."));

    private static final String FALLBACK = "Não foi possível concluir a operação.";

    private ErrorMessages() {
    }

    static String forCode(String code) {
        return MESSAGES.getOrDefault(code, FALLBACK);
    }
}
