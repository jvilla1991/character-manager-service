package com.moo.charactermanagerservice.dto;

import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionParticipant;

/**
 * One combatant as seen in the live session snapshot. Field names are flat and
 * mirror the PC JSON (hpCurrent/hpMax/hpTemp, conditions as a JSON string) so the
 * frontend's existing deserialize seam can read them — same convention as
 * {@link CampaignMemberView}.
 *
 * <p>For a PC combatant, HP/conditions are read from the canonical {@code pc}
 * row; for an NPC they come from the participant's own {@code npc*} columns.
 */
public record ParticipantView(
        Long participantId,
        Long pcId,
        boolean npc,
        boolean ownedByMe,
        boolean currentTurn,
        String name,
        String clazz,
        Short level,
        String portraitTint,
        String portraitInitials,
        Short initiative,
        boolean initRolled,
        Short orderIndex,
        Short hpMax,
        Short hpCurrent,
        Short hpTemp,
        Short ac,
        String conditions,
        Short deathSaveSuccesses,
        Short deathSaveFailures
) {
    /**
     * Build the view for a participant. {@code pc} is the canonical character for
     * a PC combatant (its HP/conditions are authoritative) and is ignored for an
     * NPC. {@code ownedByMe} / {@code currentTurn} are resolved by the caller
     * against the requesting user and the session's turn pointer.
     */
    public static ParticipantView from(SessionParticipant p, PC pc, boolean ownedByMe, boolean currentTurn) {
        boolean isNpc = p.getPcId() == null;
        boolean rolled = Boolean.TRUE.equals(p.getInitRolled());

        if (isNpc) {
            return new ParticipantView(
                    p.getId(), null, true, ownedByMe, currentTurn,
                    p.getDisplayName(), null, null, null, null,
                    p.getInitiative(), rolled, p.getOrderIndex(),
                    p.getNpcHpMax(), p.getNpcHpCurrent(), p.getNpcHpTemp(), null,
                    p.getNpcConditions(),
                    p.getDeathSaveSuccesses(), p.getDeathSaveFailures()
            );
        }

        return new ParticipantView(
                p.getId(), pc.getId(), false, ownedByMe, currentTurn,
                pc.getName(), pc.getClazz(), pc.getLevel(),
                pc.getPortraitTint(), pc.getPortraitInitials(),
                p.getInitiative(), rolled, p.getOrderIndex(),
                pc.getHpMax(), pc.getHpCurrent(), pc.getHpTemp(), pc.getAc(),
                pc.getConditions(),
                p.getDeathSaveSuccesses(), p.getDeathSaveFailures()
        );
    }
}
