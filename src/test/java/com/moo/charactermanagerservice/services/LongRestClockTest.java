package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.LongRestWaitView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** One long rest every 24 hours, counted in clock segments. */
class LongRestClockTest {

    private static Map<String, Object> record(String finishedAt, int segmentsSince) {
        Map<String, Object> record = LongRestClock.finished(finishedAt);
        for (int i = 0; i < segmentsSince; i++) {
            record = LongRestClock.tick(record);
        }
        return record;
    }

    @Test
    void noRecord_meansNoWait() {
        assertThat(LongRestClock.waitFor(Map.of(), "morning")).isNull();
        assertThat(LongRestClock.waitFor(null, "morning")).isNull();
    }

    @Test
    void aRestThatJustFinishedInTheMorning_canBeFollowedByOneTonight() {
        LongRestWaitView wait = LongRestClock.waitFor(record("morning", 0), "morning");

        assertThat(wait.finishedTimeOfDay()).isEqualTo("morning");
        assertThat(wait.segmentsSince()).isZero();
        assertThat(wait.segmentsLeft()).isEqualTo(2);
        assertThat(wait.availableTimeOfDay()).isEqualTo("night");
        assertThat(wait.availableTomorrow()).isFalse();
        assertThat(wait.reason())
                .isEqualTo("The party finished a long rest this morning. The next one can start tonight.");
    }

    @Test
    void oneSegmentLater_theWaitIsShorter_butTheAnswerIsTheSame() {
        LongRestWaitView wait = LongRestClock.waitFor(record("morning", 1), "noon");

        assertThat(wait.segmentsLeft()).isEqualTo(1);
        assertThat(wait.reason())
                .isEqualTo("The party finished a long rest this morning. The next one can start tonight.");
    }

    @Test
    void twoSegmentsLater_theWaitIsOver() {
        assertThat(LongRestClock.waitFor(record("morning", 2), "night")).isNull();
    }

    @Test
    void aRestFinishedAtNoon_opensTheNextMorning() {
        assertThat(LongRestClock.waitFor(record("noon", 0), "noon").reason())
                .isEqualTo("The party finished a long rest at noon. The next one can start tomorrow morning.");
        LongRestWaitView later = LongRestClock.waitFor(record("noon", 1), "night");
        assertThat(later.availableTimeOfDay()).isEqualTo("morning");
        assertThat(later.availableTomorrow()).isTrue();
        assertThat(later.reason())
                .isEqualTo("The party finished a long rest at noon. The next one can start tomorrow morning.");
    }

    @Test
    void aRestFinishedAtNight_opensAtNoonTheNextDay() {
        assertThat(LongRestClock.waitFor(record("night", 0), "night").reason())
                .isEqualTo("The party finished a long rest tonight. The next one can start tomorrow at noon.");
        // The clock has rolled into the next morning since.
        assertThat(LongRestClock.waitFor(record("night", 1), "morning").reason())
                .isEqualTo("The party finished a long rest last night. The next one can start at noon.");
    }

    @Test
    void tick_stopsCountingOnceTheWaitIsOver() {
        Map<String, Object> record = record("morning", 5);
        assertThat(LongRestClock.segmentsSince(record)).isEqualTo(LongRestClock.SEGMENTS_BETWEEN);
        assertThat(record).containsEntry("timeOfDay", "morning");
    }

    @Test
    void aRecordWithAnUnknownSegment_stillWaits_usingTheClockAsItStands() {
        Map<String, Object> odd = Map.of("timeOfDay", "dusk", "segmentsSince", 0);
        LongRestWaitView wait = LongRestClock.waitFor(odd, "noon");
        assertThat(wait.finishedTimeOfDay()).isEqualTo("noon");
        assertThat(wait.segmentsLeft()).isEqualTo(2);
    }

    @Test
    void anUnreadableClock_meansNoWait() {
        assertThat(LongRestClock.waitFor(record("morning", 0), "null")).isNull();
    }
}
