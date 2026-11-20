package com.moo.charactermanagerservice.models;

/**
 * What kind of change a {@link SessionCombatLog} row records — the DM's panel
 * colours a row by it. A row that carries several changes takes the most
 * important one: hit points lost or regained first, then a condition, then
 * any other hit point number (temporary or maximum).
 */
public enum CombatLogType {
    /** Current hit points went down (or temporary hit points absorbed a hit). */
    DAMAGE,
    /** Current hit points went up. */
    HEALING,
    /** A condition was added or removed, or the exhaustion level moved. */
    CONDITION,
    /** Temporary or maximum hit points changed, with current hit points untouched. */
    HIT_POINTS
}
