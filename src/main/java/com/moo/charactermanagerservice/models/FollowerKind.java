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
 *   <li>{@code COMPANION} — a companion the server owns (a Vestige Patron's
 *       vestige). The server creates it, rebuilds its stat block and removes
 *       it; no request creates one, and nobody dismisses one, the DM
 *       included. The owning player and the DM change only its name, its
 *       Hit Points and its conditions.</li>
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
