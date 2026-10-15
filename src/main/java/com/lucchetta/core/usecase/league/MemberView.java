package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.league.LeagueMember;

public record MemberView(LeagueMember member, String displayName) {
}
