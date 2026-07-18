package com.moo.charactermanagerservice.dto;

/** Rename / re-note a curated loot list (a blank name is ignored). */
public record UpdateLootRequest(String name, String notes) {}
