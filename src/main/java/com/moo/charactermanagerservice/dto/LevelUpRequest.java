package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * Optional player choices supplied when committing a level-up. The body is optional — levels
 * with no choices send nothing.
 *
 * <p>Phase 3: {@code subclass} (the chosen subclass name when a class reaches its
 * subclass-selection level). Phase 4: {@code abilityIncreases} — the Ability Score Improvement
 * allocation as {@code ABILITY -> points}, e.g. {@code {"CON":2}} or {@code {"STR":1,"DEX":1}}.
 * Feats: {@code feat} — the chosen General feat name, the alternative to an ASI at an ASI level
 * (exactly one of {@code abilityIncreases} / {@code feat} is supplied there). Later phases extend
 * this (spell selections) rather than widening the URL.
 */
public record LevelUpRequest(String subclass, Map<String, Integer> abilityIncreases, String feat) {}
