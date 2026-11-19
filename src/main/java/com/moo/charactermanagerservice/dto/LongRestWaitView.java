package com.moo.charactermanagerservice.dto;

/**
 * Why the party cannot start a long rest yet: one long rest every 24 hours,
 * counted on the campaign clock. Rides the session snapshot as
 * {@code longRestWait}; null there means a long rest may start.
 *
 * <p>{@code finishedTimeOfDay} is the segment (morning | noon | night) the last
 * long rest finished in, {@code segmentsSince} how many segments the clock has
 * moved since, and {@code segmentsLeft} how many more it must move.
 * {@code availableTimeOfDay} is the segment the next long rest can start in,
 * and {@code availableTomorrow} whether that is after the next night → morning
 * rollover. {@code reason} is the same thing as a sentence for players, e.g.
 * "The party finished a long rest this morning. The next one can start tonight."
 */
public record LongRestWaitView(
        String finishedTimeOfDay,
        int segmentsSince,
        int segmentsLeft,
        String availableTimeOfDay,
        boolean availableTomorrow,
        String reason
) {}
