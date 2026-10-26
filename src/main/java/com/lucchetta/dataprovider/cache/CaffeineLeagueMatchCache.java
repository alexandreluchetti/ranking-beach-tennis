package com.lucchetta.dataprovider.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lucchetta.core.port.LeagueMatchCache;
import com.lucchetta.core.usecase.match.MatchDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * In-memory {@link LeagueMatchCache} (the API runs as a single instance). The adapters that write
 * matches, seasons and player names evict it; inside a transaction the eviction waits for the
 * commit, so a concurrent reader cannot cache the data from before it. A load running when the
 * eviction comes is discarded too (Caffeine evicts a key only after its load finishes).
 * Entries also expire after a while, a safety net for changes made outside the application.
 */
@Component
public class CaffeineLeagueMatchCache implements LeagueMatchCache {

    private static final int MAX_LEAGUES = 500;
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);

    private final Cache<UUID, List<MatchDetails>> cache = Caffeine.newBuilder()
            .maximumSize(MAX_LEAGUES)
            .expireAfterWrite(TIME_TO_LIVE)
            .build();

    @Override
    public List<MatchDetails> get(UUID leagueId, Supplier<List<MatchDetails>> loader) {
        return cache.get(leagueId, id -> loader.get());
    }

    /** The league's matches changed (match or season written). */
    public void evict(UUID leagueId) {
        afterCommit(() -> cache.invalidate(leagueId));
    }

    /** Data shown in every league changed (a player's display name). */
    public void evictAll() {
        afterCommit(cache::invalidateAll);
    }

    private static void afterCommit(Runnable eviction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eviction.run();
            }
        });
    }
}
