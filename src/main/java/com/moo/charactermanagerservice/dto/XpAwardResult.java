package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * Result of a DM XP award — one {@link Entry} per affected PC. A single-PC award
 * returns one entry; "give to all" returns one per seated PC. XP is intentionally
 * not surfaced on the live session snapshot, so this payload carries the new
 * total(s) for the DM's confirmation toast.
 */
public record XpAwardResult(List<Entry> awarded) {

    /**
     * @param pcId  the affected character
     * @param name  the character's name (for the toast)
     * @param xp    the new total after the award
     * @param delta the actual change applied — may differ from the requested
     *              amount when the total was floored at 0
     */
    public record Entry(Long pcId, String name, Integer xp, Integer delta) {}
}
