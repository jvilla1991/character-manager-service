package com.moo.charactermanagerservice.dto;

/**
 * Set or clear a World of Warcraft Hunter's pet family ({@code PUT /api/v1/pc/{id}/pet}).
 * {@code family} is one of the pet family names (e.g. {@code "Wolf"}); {@code null} or a blank
 * name means "no pet".
 */
public record SetPetRequest(String family) {}
