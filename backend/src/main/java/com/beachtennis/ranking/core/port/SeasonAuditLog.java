package com.beachtennis.ranking.core.port;

import com.beachtennis.ranking.core.domain.SeasonAuditEntry;

public interface SeasonAuditLog {

    void record(SeasonAuditEntry entry);
}
