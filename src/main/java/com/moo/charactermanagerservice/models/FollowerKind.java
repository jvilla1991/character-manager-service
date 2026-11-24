package com.moo.charactermanagerservice.models;

/**
 * What a follower is (V64), which decides who may create and dismiss it.
 * Stored as its name in {@code pc_follower.kind}.
 *
 * <ul>
 *   <li>{@code DM_GRANT}  — a companion the campaign's DM grants; the DM alone
 *       creates and dismisses it. Every follower from before V64.</li>
 *   <li>{@code SUMMON}    — a creature the player's own spell conjures; the
 *       owning player or the campaign's DM creates and dismisses it.</li>
 *   <li>{@code FAMILIAR}  — the character's familiar, same authority as a
 *       summon; a character has one, and a new one replaces the old.</li>
 *   <li>{@code COMPANION} — reserved for server-owned companions. Nothing
 *       creates one through the API yet; the DM dismisses it like a grant.</li>
 * </ul>
 */
public enum FollowerKind {
    DM_GRANT,
    SUMMON,
    FAMILIAR,
    COMPANION;

    /** Whether the owning player may create and dismiss a follower of this kind. */
    public boolean playerManaged() {
        return this == SUMMON || this == FAMILIAR;
    }
}
