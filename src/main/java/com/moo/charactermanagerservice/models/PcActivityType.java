package com.moo.charactermanagerservice.models;

/** The kinds of events recorded on a character's {@link PcActivityLog}. */
public enum PcActivityType {
    LEVEL_UP,
    PURCHASE,
    SALE,
    LOOT,
    XP_AWARD,
    LONG_REST,
    SHORT_REST,
    INSPIRATION,
    DM_EDIT
}
