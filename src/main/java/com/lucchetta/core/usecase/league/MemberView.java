package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.league.LeagueMember;

/**
 * @param fullName the member's full name, only for callers who administer the league (D-024);
 *                 otherwise null, as for members who have not filled it in
 */
public record MemberView(LeagueMember member, String displayName, String fullName) {
}
