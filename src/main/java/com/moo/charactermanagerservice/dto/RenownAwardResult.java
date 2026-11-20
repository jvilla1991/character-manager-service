package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * Result of a DM's session-wide renown reward — one {@link Entry} per rewarded
 * character, mirroring {@link XpAwardResult} so the DM's confirmation reads the
 * same way for both reward types.
 */
public record RenownAwardResult(List<Entry> awarded) {

    /**
     * @param pcId  the affected character
     * @param name  the character's name
     * @param score the character's score with the faction after the reward
     * @param delta the actual change applied — may differ from the requested
     *              amount when the score was clamped at -100 or 100
     */
    public record Entry(Long pcId, String name, int score, int delta) {}
}
