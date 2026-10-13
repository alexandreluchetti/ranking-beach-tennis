package com.lucchetta.core.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Business dates are evaluated in Brasília time (decision D-006).
 */
public final class BusinessCalendar {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private BusinessCalendar() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
