package com.moo.charactermanagerservice.dto;

/**
 * DM sets the campaign clock directly. Date parts are free-text labels
 * ("1492 DR" / "Hammer" / "3rd") — any homebrew calendar; {@code weekday} is
 * the free-text day-of-week whose repetition drives the week counter.
 */
public record SetTimeRequest(String year, String month, String day, String timeOfDay, String weekday) {}
