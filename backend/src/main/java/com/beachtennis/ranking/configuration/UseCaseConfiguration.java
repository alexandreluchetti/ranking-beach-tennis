package com.beachtennis.ranking.configuration;

import com.beachtennis.ranking.core.port.PlayerRepository;
import com.beachtennis.ranking.core.usecase.GetOrCreateCurrentPlayer;
import com.beachtennis.ranking.core.usecase.UpdateDisplayName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the framework-free use cases from the core package.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GetOrCreateCurrentPlayer getOrCreateCurrentPlayer(PlayerRepository playerRepository, Clock clock) {
        return new GetOrCreateCurrentPlayer(playerRepository, clock);
    }

    @Bean
    UpdateDisplayName updateDisplayName(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                                        PlayerRepository playerRepository,
                                        Clock clock) {
        return new UpdateDisplayName(getOrCreateCurrentPlayer, playerRepository, clock);
    }
}
