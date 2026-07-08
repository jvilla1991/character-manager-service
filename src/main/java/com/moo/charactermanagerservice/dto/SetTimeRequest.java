package com.moo.charactermanagerservice.dto;

/**
 * DM sets the campaign clock directly. Date parts are free-text labels
 * ("1492 DR" / "Hammer" / "3rd") — any homebrew calendar; {@code weekday} is
 * the free-text day-of-week whose repetition drives the week counter.
 *
 * <p>{@code week} applies only to campaigns with a defined week (see the
 * campaign's {@code weekDays}): non-null sets the week counter directly
 * (floored at 1), null keeps the current week. Without a definition the field
 * is ignored entirely — repetition keeps driving the counter.
 */
public record SetTimeRequest(String year, String month, String day, String timeOfDay, String weekday,
                             Integer week) {}
