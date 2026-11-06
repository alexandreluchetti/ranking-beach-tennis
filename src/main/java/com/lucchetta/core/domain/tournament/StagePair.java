package com.lucchetta.core.domain.tournament;

/**
 * A pair drawn for a whole stage (D-027), made of two entrants (by position).
 *
 * @param number draw order, from 1; the last tie-break inside a group
 * @param group  group number, from 1
 */
public record StagePair(int number, int group, int firstPosition, int secondPosition) {
}
