package com.beachtennis.ranking.core.usecase.match;

import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import com.beachtennis.ranking.core.domain.BusinessCalendar;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchStatus;
import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Score;
import com.beachtennis.ranking.core.exception.ConflictException;
import com.beachtennis.ranking.core.exception.PossibleDuplicateMatchException;
import com.beachtennis.ranking.core.exception.RuleViolationException;
import com.beachtennis.ranking.core.port.MatchRepository;
import com.beachtennis.ranking.core.port.PlayerRepository;
import com.beachtennis.ranking.core.port.TransactionRunner;
import com.beachtennis.ranking.core.usecase.player.GetOrCreateCurrentPlayer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registers a finished match. Any authenticated user may do it.
 * <ul>
 *     <li>Idempotent by client-generated id: resending identical content returns the stored match
 *     without creating anything; different content under the same id is a conflict.</li>
 *     <li>Matches more than 10 minutes in the future are rejected (D-011).</li>
 *     <li>A same-day match with the same teams requires explicit confirmation (D-012).</li>
 * </ul>
 */
public class RegisterMatch {

    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(10);

    public record Command(
            UUID id,
            Instant playedAt,
            Score score,
            List<Participant> participants,
            boolean confirmPossibleDuplicate) {
    }

    /** @param created false when the request was an idempotent resend */
    public record Result(MatchDetails details, boolean created) {
    }

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final MatchDetailsAssembler assembler;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public RegisterMatch(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                         MatchRepository matchRepository,
                         PlayerRepository playerRepository,
                         MatchDetailsAssembler assembler,
                         TransactionRunner transactionRunner,
                         Clock clock) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.assembler = assembler;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public Result execute(AuthenticatedUser user, Command command) {
        Objects.requireNonNull(command.id(), "id");
        Player registeredBy = getOrCreateCurrentPlayer.execute(user);
        Instant now = clock.instant();
        Match match = new Match(command.id(), command.playedAt(), command.score(), command.participants(),
                MatchStatus.VALID, registeredBy.id(), now, now);

        return transactionRunner.inTransaction(() -> {
            Match existing = matchRepository.findById(match.id()).orElse(null);
            if (existing != null) {
                return idempotentResend(existing, match);
            }
            validateNew(match, now, command.confirmPossibleDuplicate());
            if (!matchRepository.insertIfAbsent(match)) {
                // Lost a race against a concurrent resend of the same id.
                return idempotentResend(matchRepository.findById(match.id()).orElseThrow(), match);
            }
            return new Result(assembler.assemble(match), true);
        });
    }

    private Result idempotentResend(Match existing, Match requested) {
        if (!existing.hasSameContentAs(requested)) {
            throw new ConflictException("MATCH_ID_CONFLICT");
        }
        return new Result(assembler.assemble(existing), false);
    }

    private void validateNew(Match match, Instant now, boolean duplicateConfirmed) {
        if (match.playedAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new RuleViolationException("MATCH_IN_FUTURE");
        }
        Set<UUID> playerIds = match.participants().stream()
                .map(Participant::playerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (playerRepository.findAllByIds(playerIds).size() != playerIds.size()) {
            throw new RuleViolationException("UNKNOWN_PLAYER");
        }
        if (!duplicateConfirmed) {
            List<UUID> duplicates = sameDayMatches(match.playedOn()).stream()
                    .filter(match::hasSameTeamsAs)
                    .map(Match::id)
                    .toList();
            if (!duplicates.isEmpty()) {
                throw new PossibleDuplicateMatchException(duplicates);
            }
        }
    }

    private List<Match> sameDayMatches(LocalDate day) {
        Instant from = day.atStartOfDay(BusinessCalendar.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(BusinessCalendar.ZONE).toInstant();
        return matchRepository.findValidPlayedBetween(from, to);
    }
}
