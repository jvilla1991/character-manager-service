package com.moo.charactermanagerservice.dto;

/** A player improves their seated PC's survival condition (EAT/DRINK/SLEEP_*). */
public record ConsumeSurvivalRequest(Long pcId, String action) {}
