package com.moo.charactermanagerservice.dto;

/** DM sets the campaign clock directly (no condition bumps). All fields required. */
public record SetTimeRequest(Integer year, Integer month, Integer day, String timeOfDay) {}
