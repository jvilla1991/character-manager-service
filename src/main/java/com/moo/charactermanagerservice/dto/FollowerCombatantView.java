package com.moo.charactermanagerservice.dto;

import com.moo.charactermanagerservice.models.PcFollower;

import java.util.List;
import java.util.Map;

/**
 * A follower as it rides in the live session snapshot, nested under its
 * owner's {@link ParticipantView}. A follower shares its owner's turn: it has
 * no initiative and no participant row of its own, so it only carries what the
 * tracker shows at a glance — who it is and how hurt it is.
 *
 * <p>The values are lifted out of the follower's opaque stat block (the
 * client's sheet shape, e.g. {@code {"hp":{"cur":9,"max":16,"temp":0},"ac":16}}).
 * Reading is deliberately forgiving: a missing or wrongly-typed field becomes
 * null (or an empty {@code conditions} list) rather than failing the whole
 * snapshot over one hand-edited follower.
 */
public record FollowerCombatantView(
        long followerId,
        long pcId,
        String name,
        String clazz,
        Integer level,
        String portraitTint,
        String portraitInitials,
        Integer hpMax,
        Integer hpCurrent,
        Integer hpTemp,
        Integer ac,
        List<String> conditions
) {
    /**
     * Build the view from a follower and its already-parsed stat block
     * ({@code statBlock} is empty when the stored JSON was unreadable).
     */
    public static FollowerCombatantView from(PcFollower follower, Map<String, Object> statBlock) {
        Map<?, ?> hp = statBlock.get("hp") instanceof Map<?, ?> m ? m : Map.of();
        return new FollowerCombatantView(
                follower.getId(), follower.getPcId(), follower.getName(),
                text(statBlock.get("clazz")),
                whole(statBlock.get("level")),
                text(statBlock.get("portraitTint")),
                text(statBlock.get("portraitInitials")),
                whole(hp.get("max")), whole(hp.get("cur")), whole(hp.get("temp")),
                whole(statBlock.get("ac")),
                strings(statBlock.get("conditions"))
        );
    }

    private static String text(Object value) {
        return value instanceof String s ? s : null;
    }

    /** A JSON number (or a numeric string) as an int; anything else is null. */
    public static Integer whole(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
