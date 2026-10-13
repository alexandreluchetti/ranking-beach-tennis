package com.beachtennis.ranking.core.usecase.notification;

import com.beachtennis.ranking.core.domain.BusinessCalendar;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchAuditEntry;
import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.scoring.NotCountedReason;
import com.beachtennis.ranking.core.usecase.match.MatchDetails;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes the user-facing (pt-BR) text of match notifications for one recipient. Notifications
 * are stored as text so the in-app history and push messages say exactly the same thing.
 */
public class MatchNotificationComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public record Message(String title, String body) {
    }

    public Message registered(MatchDetails details, UUID recipientId) {
        String outcome = participates(details, recipientId)
                ? participation(details, recipientId)
                : eligibilitySummary(details);
        return new Message("Partida registrada", "%s: %s. %s".formatted(day(details), teams(details), outcome));
    }

    public Message changed(MatchAuditEntry.Action action, MatchDetails before, MatchDetails after, String reason,
                           UUID recipientId) {
        StringBuilder body = new StringBuilder();
        String title = switch (action) {
            case CORRECTED -> {
                body.append("%s: a partida %s foi corrigida pelo administrador. Mudanças: %s."
                        .formatted(day(before), teams(before), String.join("; ", changes(before, after))));
                yield "Partida corrigida";
            }
            case VOIDED -> {
                body.append("%s: a partida %s foi anulada pelo administrador e não conta mais no ranking "
                        .formatted(day(before), teams(before)))
                        .append("nem nas estatísticas.");
                yield "Partida anulada";
            }
            case VOID_REVERTED -> {
                body.append("%s: a anulação da partida %s foi revertida e ela voltou a valer."
                        .formatted(day(after), teams(after)));
                yield "Anulação revertida";
            }
        };
        if (reason != null) {
            body.append(" Motivo: ").append(reason);
            if (!reason.endsWith(".")) {
                body.append('.');
            }
        }
        body.append(' ').append(effectOn(before, after, recipientId));
        return new Message(title, body.toString());
    }

    private String effectOn(MatchDetails before, MatchDetails after, UUID recipientId) {
        boolean wasIn = participates(before, recipientId);
        boolean isIn = participates(after, recipientId);
        if (!wasIn && !isIn) {
            return after.eligibility().counted() ? "Agora: " + teams(after) + "."
                    : "Agora: %s. %s".formatted(teams(after), notCounted(after.eligibility().reason()));
        }
        List<String> parts = new ArrayList<>();
        if (wasIn && !isIn) {
            parts.add("Você não faz mais parte desta partida.");
        } else if (!wasIn) {
            parts.add("Você foi incluído nesta partida.");
        }
        parts.add("Seus pontos nesta partida: %d → %d.".formatted(points(before, recipientId), points(after, recipientId)));
        if (isIn && !after.eligibility().counted() && after.eligibility().reason() != NotCountedReason.VOIDED) {
            parts.add(notCounted(after.eligibility().reason()));
        }
        return String.join(" ", parts);
    }

    private static List<String> changes(MatchDetails before, MatchDetails after) {
        Match b = before.match();
        Match a = after.match();
        List<String> changes = new ArrayList<>();
        if (!b.playedAt().equals(a.playedAt())) {
            changes.add("data %s → %s".formatted(dateTime(b), dateTime(a)));
        }
        if (b.format() != a.format()) {
            changes.add("formato %s → %s".formatted(formatName(b), formatName(a)));
        }
        if (!b.score().equals(a.score())) {
            changes.add("placar %s → %s".formatted(score(b), score(a)));
        }
        if (!b.participants().equals(a.participants())) {
            changes.add("duplas %s → %s".formatted(pairs(before), pairs(after)));
        }
        return changes;
    }

    private static String participation(MatchDetails details, UUID recipientId) {
        Team team = teamOf(details.match(), recipientId);
        boolean won = details.match().winner() == team;
        if (details.eligibility().counted()) {
            return "Você %s e recebeu %s.".formatted(won ? "venceu" : "perdeu", pointsText(details.pointsFor(team)));
        }
        return "Você %s. %s".formatted(won ? "venceu" : "perdeu", notCounted(details.eligibility().reason()));
    }

    private static String eligibilitySummary(MatchDetails details) {
        return details.eligibility().counted() ? "Partida válida para o ranking."
                : notCounted(details.eligibility().reason());
    }

    private static String notCounted(NotCountedReason reason) {
        return switch (reason) {
            case GUEST_PRESENT -> "A partida não vale pontos porque havia um convidado entre os participantes.";
            case NO_SEASON -> "A partida não vale pontos porque não há temporada nessa data.";
            case VOIDED -> "A partida está anulada.";
        };
    }

    private static boolean participates(MatchDetails details, UUID playerId) {
        return details.match().playerIds().contains(playerId);
    }

    private static int points(MatchDetails details, UUID playerId) {
        return participates(details, playerId) ? details.pointsFor(teamOf(details.match(), playerId)) : 0;
    }

    private static Team teamOf(Match match, UUID playerId) {
        return match.participants().stream()
                .filter(p -> playerId.equals(p.playerId()))
                .findFirst().orElseThrow().team();
    }

    private static String pointsText(int points) {
        return points == 1 ? "1 ponto" : points + " pontos";
    }

    /** "Ana e Beto 6–4 Caio e Convidado". */
    private static String teams(MatchDetails details) {
        Match match = details.match();
        return "%s %d–%d %s".formatted(names(details, Team.A), match.score().teamAGames(),
                match.score().teamBGames(), names(details, Team.B));
    }

    private static String pairs(MatchDetails details) {
        return names(details, Team.A) + " x " + names(details, Team.B);
    }

    private static String names(MatchDetails details, Team team) {
        return details.match().team(team).stream()
                .map(p -> name(details, p))
                .collect(Collectors.joining(" e "));
    }

    private static String name(MatchDetails details, Participant p) {
        if (p.isGuest()) {
            return p.guestName() == null ? "Convidado" : p.guestName() + " (convidado)";
        }
        return details.playerNames().getOrDefault(p.playerId(), "Jogador");
    }

    private static String score(Match match) {
        return match.score().teamAGames() + "–" + match.score().teamBGames();
    }

    private static String formatName(Match match) {
        return switch (match.format()) {
            case STANDARD -> "tradicional";
            case SHORT -> "curto";
        };
    }

    private static String day(MatchDetails details) {
        return DATE.format(details.match().playedAt().atZone(BusinessCalendar.ZONE));
    }

    private static String dateTime(Match match) {
        return DATE_TIME.format(match.playedAt().atZone(BusinessCalendar.ZONE));
    }
}
