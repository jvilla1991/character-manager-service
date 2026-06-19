package com.moo.charactermanagerservice.dto;

/**
 * How a level-up's hit-point gain is determined.
 *
 * <p>{@link #AVERAGE} (the default) takes the fixed hit-die average — the existing behaviour, so a
 * request that omits the mode is unchanged. {@link #ROLL} rolls the hit die instead. In ROLL mode
 * the <strong>server</strong> performs the roll; a client-supplied result is never trusted, so the
 * client cannot inflate its HP. Both modes add the CON modifier and floor the per-level gain at 1.
 */
public enum HpMode {
    AVERAGE,
    ROLL
}
