package com.lucchetta.dataprovider.cache;

import com.lucchetta.core.usecase.match.MatchDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineLeagueMatchCacheTest {

    private final CaffeineLeagueMatchCache cache = new CaffeineLeagueMatchCache();
    private final UUID league = UUID.randomUUID();
    private final UUID otherLeague = UUID.randomUUID();
    private final AtomicInteger loads = new AtomicInteger();
    private final Supplier<List<MatchDetails>> loader = () -> {
        loads.incrementAndGet();
        return List.of();
    };

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void loadsEachLeagueOnceUntilEvicted() {
        cache.get(league, loader);
        cache.get(league, loader);
        cache.get(otherLeague, loader);
        assertThat(loads).hasValue(2);

        cache.evict(league);
        cache.get(league, loader);
        cache.get(otherLeague, loader);
        assertThat(loads).hasValue(3);

        cache.evictAll();
        cache.get(league, loader);
        cache.get(otherLeague, loader);
        assertThat(loads).hasValue(5);
    }

    @Test
    void insideATransactionTheEvictionWaitsForTheCommit() {
        cache.get(league, loader);
        TransactionSynchronizationManager.initSynchronization();

        cache.evict(league);
        cache.get(league, loader); // a reader before the commit still gets the cached copy
        assertThat(loads).hasValue(1);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        cache.get(league, loader);
        assertThat(loads).hasValue(2);
    }

    @Test
    void aRolledBackTransactionKeepsTheCache() {
        cache.get(league, loader);
        TransactionSynchronizationManager.initSynchronization();

        cache.evictAll();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        cache.get(league, loader);

        assertThat(loads).hasValue(1);
    }
}
