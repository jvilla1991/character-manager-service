package com.moo.charactermanagerservice.dto;

/**
 * Optional player choices supplied when committing a level-up. The body is optional — levels
 * with no choices send nothing. Phase 3 adds {@code subclass} (the chosen subclass name when a
 * class reaches its subclass-selection level). Later phases extend this with ASI/feat and spell
 * selections rather than widening the URL.
 */
public record LevelUpRequest(String subclass) {}
