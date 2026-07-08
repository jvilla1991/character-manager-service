package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * DM defines (or clears) the campaign's week: an ordered list of weekday names
 * the clock walks on each night → morning rollover. Null or empty clears the
 * definition, returning the campaign to free-text weekdays.
 */
public record SetWeekDaysRequest(List<String> weekDays) {}
