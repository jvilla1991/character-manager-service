package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.LongRestWaitView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "One long rest every 24 hours", kept on the campaign clock.
 *
 * <p>The campaign clock has three segments a day (morning, noon, night — about
 * eight hours each) and free-text dates, so two clock positions cannot be
 * subtracted. Instead the campaign remembers the last finished long rest as a
 * small record, {@code campaign.last_long_rest}:
 *
 * <pre>{"timeOfDay":"morning","segmentsSince":0}</pre>
 *
 * {@code timeOfDay} is the segment the rest finished in (after any clock
 * advance that finish made) and {@code segmentsSince} counts the segments the
 * clock has moved since. A long rest takes eight hours, so a new one may START
 * once {@value #SEGMENTS_BETWEEN} segments (sixteen hours) have passed — it
 * then finishes twenty-four hours after the last one did.
 *
 * <p>No record means no limit: the campaign has never finished a long rest, the
 * rest finished on a clock nobody has set, or the DM set the date by hand
 * (which says "time has moved" without saying how far).
 */
final class LongRestClock {

    private LongRestClock() {}

    /** Segments that must pass after a long rest finishes before the next may start. */
    static final int SEGMENTS_BETWEEN = 2;

    /** The record for a long rest that has just finished in this segment. */
    static Map<String, Object> finished(String timeOfDay) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timeOfDay", timeOfDay);
        record.put("segmentsSince", 0);
        return record;
    }

    /** The record after the clock moved one segment (it stops counting once the wait is over). */
    static Map<String, Object> tick(Map<String, Object> record) {
        Map<String, Object> next = new LinkedHashMap<>(record);
        next.put("segmentsSince", Math.min(SEGMENTS_BETWEEN, segmentsSince(record) + 1));
        return next;
    }

    static int segmentsSince(Map<String, Object> record) {
        return record.get("segmentsSince") instanceof Number n ? Math.max(0, n.intValue()) : 0;
    }

    /**
     * What the party is still waiting for, or null when a long rest may start.
     * {@code record} is the parsed campaign record (empty when there is none)
     * and {@code currentTimeOfDay} the clock's segment now.
     */
    static LongRestWaitView waitFor(Map<String, Object> record, String currentTimeOfDay) {
        if (record == null || record.isEmpty() || !GameClock.isValidSegment(currentTimeOfDay)) {
            return null;
        }
        int since = segmentsSince(record);
        int left = SEGMENTS_BETWEEN - since;
        if (left <= 0) {
            return null;
        }
        String finishedAt = record.get("timeOfDay") instanceof String s && GameClock.isValidSegment(s)
                ? s : currentTimeOfDay;
        int now = GameClock.SEGMENTS.indexOf(currentTimeOfDay);
        int target = now + left;
        boolean tomorrow = target >= GameClock.SEGMENTS.size();
        String availableAt = GameClock.SEGMENTS.get(target % GameClock.SEGMENTS.size());
        return new LongRestWaitView(finishedAt, since, left, availableAt, tomorrow,
                reason(finishedAt, since, currentTimeOfDay, availableAt, tomorrow));
    }

    /**
     * "The party finished a long rest this morning. The next one can start
     * tonight." — in the clock's own words. The client builds the same
     * sentence for demo mode (utils/long-rest-limit.ts).
     */
    static String reason(String finishedAt, int since, String now, String availableAt, boolean tomorrow) {
        return "The party finished a long rest " + finishedPhrase(finishedAt, since, now)
                + ". The next one can start " + availablePhrase(availableAt, tomorrow) + ".";
    }

    private static String finishedPhrase(String finishedAt, int since, String now) {
        // The clock rolled into a new day since the rest finished.
        boolean yesterday = since > 0
                && GameClock.SEGMENTS.indexOf(now) < GameClock.SEGMENTS.indexOf(finishedAt);
        return switch (finishedAt) {
            case "morning" -> yesterday ? "yesterday morning" : "this morning";
            case "noon" -> yesterday ? "yesterday at noon" : "at noon";
            default -> yesterday ? "last night" : "tonight";
        };
    }

    private static String availablePhrase(String availableAt, boolean tomorrow) {
        return switch (availableAt) {
            case "morning" -> tomorrow ? "tomorrow morning" : "this morning";
            case "noon" -> tomorrow ? "tomorrow at noon" : "at noon";
            default -> tomorrow ? "tomorrow night" : "tonight";
        };
    }
}
