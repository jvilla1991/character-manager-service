package com.moo.charactermanagerservice.dto;

/**
 * One row of a class's level 2–20 feature roadmap: the feature (server-owned name and
 * description, like {@link FeatureGain}), the level it is gained at, and whether the character's
 * subclass grants it rather than the class itself. Read by the character sheet to show the
 * features a character has yet to unlock.
 */
public record FeatureProgressionEntry(int level, String name, String desc, boolean subclass) {}
