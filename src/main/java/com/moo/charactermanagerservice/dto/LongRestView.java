package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The open long rest phase as it rides the poll snapshot — broadcast to every
 * viewer, null when no phase is open. Recovery has NOT been applied while this
 * is present: it lands for the whole party when the DM finishes the rest or
 * the last able player marks Ready.
 *
 * <p>{@code readiness} lists every seated PC in seating order. {@code
 * cannotRest} is true for a hero at 0 hit points, who gains nothing from the
 * rest and is not waited on. {@code readyCount} / {@code ableCount} are the
 * "x of y ready" numbers (heroes who cannot rest are in neither).
 */
public record LongRestView(
        boolean advanceClock,
        boolean undisturbed,
        int readyCount,
        int ableCount,
        List<ReadinessView> readiness
) {
    public record ReadinessView(Long participantId, Long pcId, String displayName,
                                boolean ready, boolean cannotRest) {}
}
