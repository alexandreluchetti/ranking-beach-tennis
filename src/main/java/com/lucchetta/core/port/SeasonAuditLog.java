package com.lucchetta.core.port;

import com.lucchetta.core.domain.SeasonAuditEntry;

public interface SeasonAuditLog {

    void record(SeasonAuditEntry entry);
}
