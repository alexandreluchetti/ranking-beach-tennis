package com.lucchetta.core.usecase.player;

import com.lucchetta.core.domain.AuthIdentity;
import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.DisplayName;
import com.lucchetta.core.domain.FullName;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.exception.InvalidDisplayNameException;
import com.lucchetta.core.port.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProfileTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final AuthenticatedUser USER =
            new AuthenticatedUser(new AuthIdentity("firebase", "uid-1"), null, null, false);

    @Mock
    private GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    @Mock
    private PlayerRepository playerRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void renamesKeepingIdAndCreationDate() {
        Player current = new Player(UUID.randomUUID(), USER.identity(), null, new DisplayName("Ana"), CREATED, CREATED);
        when(getOrCreateCurrentPlayer.execute(USER)).thenReturn(current);
        when(playerRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Player result = useCase().execute(USER, new UpdateProfile.Command("  Ana Paula ", null));

        assertThat(result.id()).isEqualTo(current.id());
        assertThat(result.displayName().value()).isEqualTo("Ana Paula");
        assertThat(result.createdAt()).isEqualTo(CREATED);
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotWriteWhenNameIsUnchanged() {
        Player current = new Player(UUID.randomUUID(), USER.identity(), null, new DisplayName("Ana"), CREATED, CREATED);
        when(getOrCreateCurrentPlayer.execute(USER)).thenReturn(current);

        Player result = useCase().execute(USER, new UpdateProfile.Command(" Ana ", null));

        assertThat(result).isEqualTo(current);
        verify(playerRepository, never()).update(any());
    }

    @Test
    void rejectsInvalidNameBeforeTouchingStorage() {
        assertThatThrownBy(() -> useCase().execute(USER, new UpdateProfile.Command("A", null)))
                .isInstanceOf(InvalidDisplayNameException.class);
        verifyNoInteractions(getOrCreateCurrentPlayer, playerRepository);
    }

    @Test
    void setsTheFullNameKeepingTheDisplayName() {
        Player current = new Player(UUID.randomUUID(), USER.identity(), null, new DisplayName("Ana"), CREATED, CREATED);
        when(getOrCreateCurrentPlayer.execute(USER)).thenReturn(current);
        when(playerRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Player result = useCase().execute(USER, new UpdateProfile.Command(null, "  Ana   Paula  Souza "));

        assertThat(result.fullName()).isEqualTo(new FullName("Ana Paula Souza"));
        assertThat(result.displayName().value()).isEqualTo("Ana");
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void fullNameNeedsFirstAndLastName() {
        assertThatThrownBy(() -> useCase().execute(USER, new UpdateProfile.Command(null, "Ana")))
                .hasMessage("INVALID_FULL_NAME");
        assertThatThrownBy(() -> useCase().execute(USER, new UpdateProfile.Command(null, "A " + "b".repeat(99))))
                .hasMessage("INVALID_FULL_NAME");
        verifyNoInteractions(getOrCreateCurrentPlayer, playerRepository);
    }

    private UpdateProfile useCase() {
        return new UpdateProfile(getOrCreateCurrentPlayer, playerRepository, clock);
    }
}
