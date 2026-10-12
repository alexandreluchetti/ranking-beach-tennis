package com.beachtennis.ranking.core.usecase;

import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import com.beachtennis.ranking.core.domain.DisplayName;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.port.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrCreateCurrentPlayerTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final AuthIdentity IDENTITY = new AuthIdentity("firebase", "uid-1");

    @Mock
    private PlayerRepository playerRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsExistingPlayerWithoutCreating() {
        Player existing = player("Ana");
        when(playerRepository.findByIdentity(IDENTITY)).thenReturn(Optional.of(existing));

        Player result = useCase().execute(user("Outro Nome", "ana@example.com"));

        assertThat(result).isEqualTo(existing);
        verify(playerRepository, never()).insertIfAbsent(any());
    }

    @Test
    void createsPlayerUsingProviderName() {
        whenInsertedReturnStored();

        useCase().execute(user("Ana Souza", "ana@example.com"));

        Player candidate = capturedCandidate();
        assertThat(candidate.displayName().value()).isEqualTo("Ana Souza");
        assertThat(candidate.email()).isEqualTo("ana@example.com");
        assertThat(candidate.identity()).isEqualTo(IDENTITY);
        assertThat(candidate.createdAt()).isEqualTo(NOW);
    }

    @Test
    void fallsBackToEmailLocalPartWhenNameMissing() {
        whenInsertedReturnStored();

        useCase().execute(user(null, "beto.silva@example.com"));

        assertThat(capturedCandidate().displayName().value()).isEqualTo("beto.silva");
    }

    @Test
    void fallsBackToDefaultWhenNoUsableNameOrEmail() {
        whenInsertedReturnStored();

        useCase().execute(user(" ", null));

        assertThat(capturedCandidate().displayName().value())
                .isEqualTo(GetOrCreateCurrentPlayer.DEFAULT_DISPLAY_NAME);
    }

    @Test
    void truncatesLongProviderName() {
        whenInsertedReturnStored();

        useCase().execute(user("x".repeat(60), null));

        assertThat(capturedCandidate().displayName().value()).hasSize(DisplayName.MAX_LENGTH);
    }

    @Test
    void returnsStoredPlayerWhenConcurrentInsertWon() {
        Player winner = player("Criado Antes");
        when(playerRepository.findByIdentity(IDENTITY)).thenReturn(Optional.empty(), Optional.of(winner));

        Player result = useCase().execute(user("Ana", null));

        assertThat(result).isEqualTo(winner);
    }

    private GetOrCreateCurrentPlayer useCase() {
        return new GetOrCreateCurrentPlayer(playerRepository, clock);
    }

    /** First lookup finds nothing; the lookup after insert returns a stored player. */
    private void whenInsertedReturnStored() {
        when(playerRepository.findByIdentity(IDENTITY)).thenReturn(Optional.empty(), Optional.of(player("Stored")));
    }

    private Player capturedCandidate() {
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).insertIfAbsent(captor.capture());
        return captor.getValue();
    }

    private static AuthenticatedUser user(String name, String email) {
        return new AuthenticatedUser(IDENTITY, email, name, false);
    }

    private static Player player(String name) {
        return new Player(UUID.randomUUID(), IDENTITY, null, new DisplayName(name), NOW, NOW);
    }
}
