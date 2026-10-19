package com.lucchetta.core.usecase.notification;

import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.ranking.RankingFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchNotificationComposerTest {

    private final RankingFixture world = new RankingFixture();
    private final MatchNotificationComposer composer = new MatchNotificationComposer();

    @Test
    void registeredMatchTellsEachParticipantTheResultAndPoints() {
        MatchDetails details = details(world.match(LocalDate.of(2026, 10, 4), 6, 4));

        MatchNotificationComposer.Message winner = composer.registered(details, world.ana.id());
        MatchNotificationComposer.Message loser = composer.registered(details, world.caio.id());

        assertThat(winner.title()).isEqualTo("Partida registrada");
        assertThat(winner.body()).isEqualTo("04/10: Ana e Beto 6–4 Caio e Dani. Você venceu e recebeu 9 pontos.");
        assertThat(loser.body()).isEqualTo("04/10: Ana e Beto 6–4 Caio e Dani. Você perdeu e recebeu 4 pontos.");
    }

    @Test
    void guestMatchesGivePointsAndMatchesOutsideSeasonsExplainWhyNot() {
        MatchDetails guest = details(world.match(LocalDate.of(2026, 10, 4), 6, 4, MatchStatus.VALID,
                world.anaAndGuestVsCaioAndDani()));
        MatchDetails noSeason = details(world.match(LocalDate.of(2026, 5, 10), 4, 6));

        assertThat(composer.registered(guest, world.ana.id()).body()).isEqualTo(
                "04/10: Ana e Zé (convidado) 6–4 Caio e Dani. Você venceu e recebeu 9 pontos.");
        assertThat(composer.registered(noSeason, world.ana.id()).body())
                .endsWith("Você perdeu. A partida não vale pontos porque não há temporada nessa data.");
    }

    @Test
    void administratorWhoDidNotPlayGetsASummary() {
        MatchDetails details = details(world.match(LocalDate.of(2026, 10, 4), 6, 4));

        assertThat(composer.registered(details, UUID.randomUUID()).body())
                .isEqualTo("04/10: Ana e Beto 6–4 Caio e Dani. Partida válida para o ranking.");
    }

    private MatchDetails details(Match match) {
        return world.assembler.assemble(match);
    }
}
