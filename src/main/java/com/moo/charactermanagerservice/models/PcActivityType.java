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
    DM_EDIT,
    SPELL_SLOT,
    /** The player summoned or dismissed a follower of their own (a summon or a familiar). */
    FOLLOWER,
    /** The player set a choice of their subclass outside a level-up (an Arcane Shot option, a Vestige type). */
    SUBCLASS_CHOICE
}
