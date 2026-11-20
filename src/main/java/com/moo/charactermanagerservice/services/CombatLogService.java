package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.CombatLogEntryView;
import com.moo.charactermanagerservice.dto.CombatLogPageView;
import com.moo.charactermanagerservice.dto.FollowerCombatantView;
import com.moo.charactermanagerservice.models.CombatLogType;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionCombatLog;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.SessionCombatLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The live session's combat log: one row for each health or condition change
 * made while the session runs, read by the DM a page at a time. Ephemeral —
 * {@link #purge} empties it when the session ends.
 *
 * <p>Like {@link PcActivityLogService}, the write methods join the caller's
 * transaction: a row is saved with the change it describes or not at all, so
 * there is deliberately no try/catch and no {@code REQUIRES_NEW} here. The
 * wording code is null-safe instead, so an odd or missing value never throws.
 *
 * <p>This service only writes and reads rows. Who may read them, and waking
 * the session's poll after a write, are the callers' business: every caller
 * already holds the session and bumps its version for the change it is
 * recording.
 */
@Service
public class CombatLogService {

    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 50;

    /** Appended to a hit that takes a hero or a follower from above 0 to 0 hit points. */
    public static final String DOWN = "Down at 0 HP.";
    /** Appended to the hit that takes an enemy out of the initiative order. */
    public static final String DEFEATED = "Defeated.";

    private final SessionCombatLogRepository combatLogRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public CombatLogService(SessionCombatLogRepository combatLogRepository, ObjectMapper objectMapper) {
        this.combatLogRepository = combatLogRepository;
        this.objectMapper = objectMapper;
    }

    /** Current and temporary hit points at one moment; a missing number counts as 0. */
    public record Hp(int current, int temp) {
        public static Hp of(Number current, Number temp) {
            return new Hp(current == null ? 0 : current.intValue(), temp == null ? 0 : temp.intValue());
        }
    }

    /**
     * Everything the log watches on a character or follower sheet at one
     * moment. Taken before and after a save and compared; a missing number
     * counts as 0, and {@code conditions} is never null.
     */
    public record Vitals(int hpCurrent, int hpTemp, int hpMax, List<String> conditions, int exhaustion) {}

    /** A character's vitals as stored right now. */
    public Vitals vitalsOf(PC pc) {
        return new Vitals(whole(pc.getHpCurrent()), whole(pc.getHpTemp()), whole(pc.getHpMax()),
                parseConditions(pc.getConditions()), whole(pc.getExhaustion()));
    }

    /**
     * A follower's vitals, lifted out of its stat block (the client's sheet
     * shape: {@code {"hp":{"cur":9,"max":16,"temp":0},"conditions":[...]}}).
     * Followers have no exhaustion level.
     */
    public Vitals vitalsOfStatBlock(Map<String, Object> statBlock) {
        Map<?, ?> hp = statBlock != null && statBlock.get("hp") instanceof Map<?, ?> m ? m : Map.of();
        Object conditions = statBlock == null ? null : statBlock.get("conditions");
        return new Vitals(whole(FollowerCombatantView.whole(hp.get("cur"))),
                whole(FollowerCombatantView.whole(hp.get("temp"))),
                whole(FollowerCombatantView.whole(hp.get("max"))),
                strings(conditions), 0);
    }

    /**
     * How a follower is named in the log: "Wolf (Thorin's follower)". A
     * follower has no row of its own in the initiative order, so its owner's
     * name is what tells the DM which Wolf this is.
     */
    public static String followerSubject(String followerName, String ownerName) {
        String name = followerName == null || followerName.isBlank() ? "Follower" : followerName;
        return ownerName == null || ownerName.isBlank() ? name : name + " (" + ownerName + "'s follower)";
    }

    /** True when the two moments differ in anything the log records. */
    public boolean differ(Vitals before, Vitals after) {
        return diff(before, after) != null;
    }

    // --- writes ---------------------------------------------------------------

    /**
     * The DM damaged (positive {@code amount}) or healed (negative) a
     * combatant from the initiative order. Nothing is logged when no number
     * moved — a heal at full hit points, or a hit on a row already at 0.
     *
     * @param atZero what to add when the hit took current hit points from
     *               above 0 to 0 ({@link #DOWN} or {@link #DEFEATED}); null for nothing
     */
    public void logHpDelta(CombatSession session, String subject, int amount,
                           Hp before, Hp after, String atZero, UUID actorUserId) {
        if (amount > 0) {
            int absorbed = before.temp() - after.temp();
            int lost = before.current() - after.current();
            if (absorbed <= 0 && lost <= 0) {
                return;
            }
            StringBuilder text = new StringBuilder("Took ").append(amount).append(" damage");
            if (absorbed > 0 && lost > 0) {
                text.append(", ").append(absorbed).append(" absorbed by temporary HP");
            } else if (absorbed > 0) {
                text.append(", all absorbed by temporary HP (")
                        .append(before.temp()).append(" → ").append(after.temp()).append(" left)");
            }
            text.append('.');
            if (lost > 0) {
                text.append(" HP ").append(before.current()).append(" → ").append(after.current()).append('.');
                if (after.current() == 0 && atZero != null) {
                    text.append(' ').append(atZero);
                }
            }
            save(session, CombatLogType.DAMAGE, subject, text.toString(), actorUserId);
        } else if (amount < 0) {
            int healed = after.current() - before.current();
            if (healed <= 0) {
                return;
            }
            save(session, CombatLogType.HEALING, subject,
                    "Healed " + healed + ". HP " + before.current() + " → " + after.current() + ".",
                    actorUserId);
        }
    }

    /** A player spent a Hit Die during a short rest. Not logged when it healed nothing. */
    public void logHitDie(CombatSession session, String subject, Hp before, Hp after, UUID actorUserId) {
        int healed = after.current() - before.current();
        if (healed <= 0) {
            return;
        }
        save(session, CombatLogType.HEALING, subject,
                "Spent a Hit Die and healed " + healed + ". HP "
                        + before.current() + " → " + after.current() + ".",
                actorUserId);
    }

    /** A long rest finished for this hero. Not logged when it changed nothing the log records. */
    public void logLongRest(CombatSession session, String subject, Vitals before, Vitals after,
                            UUID actorUserId) {
        Diff diff = diff(before, after);
        if (diff == null) {
            return;
        }
        save(session, diff.type(), subject, "Finished a long rest. " + diff.text(), actorUserId);
    }

    /**
     * A sheet was saved while its character sits in a live session, and the
     * save changed hit points, conditions or exhaustion. One row carries the
     * whole save. Returns false, and logs nothing, when the two moments match.
     *
     * @param byDm true when the campaign's DM made the save, false for the player
     */
    public boolean logSheetChange(CombatSession session, String subject, Vitals before, Vitals after,
                                  boolean byDm, UUID actorUserId) {
        Diff diff = diff(before, after);
        if (diff == null) {
            return false;
        }
        save(session, diff.type(), subject,
                diff.text() + " Changed on the sheet by the " + (byDm ? "DM." : "player."),
                actorUserId);
        return true;
    }

    /** Empty the session's log — called on both paths that end a session. */
    public void purge(Long sessionId) {
        combatLogRepository.deleteBySessionId(sessionId);
    }

    // --- reads ----------------------------------------------------------------

    /**
     * One page of the session's log, newest first. {@code page} is zero-based
     * (null or negative reads as 0); {@code size} is clamped to
     * 1..{@link #MAX_PAGE_SIZE} (null reads as {@link #DEFAULT_PAGE_SIZE}). A
     * page past the end comes back empty with the true {@code total}, so the
     * client can step back to the last real page.
     */
    public CombatLogPageView page(Long sessionId, Integer page, Integer size) {
        int pageIndex = page == null || page < 0 ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        Page<SessionCombatLog> rows = combatLogRepository
                .findBySessionIdOrderByIdDesc(sessionId, PageRequest.of(pageIndex, pageSize));
        List<CombatLogEntryView> entries = rows.getContent().stream()
                .map(row -> new CombatLogEntryView(row.getId(),
                        row.getEventType() == null ? null : row.getEventType().name(),
                        row.getSubjectName(), row.getDescription(), row.getRound(), row.getCreatedAt()))
                .toList();
        return new CombatLogPageView(entries, pageIndex, pageSize, rows.getTotalElements());
    }

    // --- helpers --------------------------------------------------------------

    private void save(CombatSession session, CombatLogType type, String subject, String description,
                      UUID actorUserId) {
        SessionCombatLog row = new SessionCombatLog();
        row.setSessionId(session.getId());
        row.setEventType(type);
        row.setSubjectName(subject == null || subject.isBlank() ? "Unknown" : subject);
        row.setDescription(description);
        // A round only means something while an encounter is running.
        row.setRound(session.getStatus() == SessionStatus.ACTIVE ? session.getRound() : null);
        row.setActorUserId(actorUserId);
        combatLogRepository.save(row);
    }

    /** What changed between two moments, worded, and the row type it earns. */
    private record Diff(CombatLogType type, String text) {}

    /**
     * Compare two moments. Null when nothing the log records differs. The
     * type follows the most important change: current hit points, then a
     * condition or exhaustion, then temporary or maximum hit points.
     */
    private static Diff diff(Vitals before, Vitals after) {
        List<String> sentences = new ArrayList<>();
        CombatLogType type = null;

        if (before.hpCurrent() != after.hpCurrent()) {
            sentences.add("HP " + before.hpCurrent() + " → " + after.hpCurrent() + ".");
            type = after.hpCurrent() < before.hpCurrent() ? CombatLogType.DAMAGE : CombatLogType.HEALING;
        }
        if (before.hpTemp() != after.hpTemp()) {
            sentences.add("Temporary HP " + before.hpTemp() + " → " + after.hpTemp() + ".");
        }
        if (before.hpMax() != after.hpMax()) {
            sentences.add("Maximum HP " + before.hpMax() + " → " + after.hpMax() + ".");
        }

        List<String> added = after.conditions().stream()
                .filter(c -> !before.conditions().contains(c)).distinct().toList();
        List<String> removed = before.conditions().stream()
                .filter(c -> !after.conditions().contains(c)).distinct().toList();
        if (!added.isEmpty()) {
            sentences.add((added.size() == 1 ? "Condition added: " : "Conditions added: ")
                    + String.join(", ", added) + ".");
        }
        if (!removed.isEmpty()) {
            sentences.add((removed.size() == 1 ? "Condition removed: " : "Conditions removed: ")
                    + String.join(", ", removed) + ".");
        }
        if (before.exhaustion() != after.exhaustion()) {
            sentences.add("Exhaustion " + before.exhaustion() + " → " + after.exhaustion() + ".");
        }

        if (sentences.isEmpty()) {
            return null;
        }
        if (type == null) {
            boolean condition = !added.isEmpty() || !removed.isEmpty()
                    || before.exhaustion() != after.exhaustion();
            type = condition ? CombatLogType.CONDITION : CombatLogType.HIT_POINTS;
        }
        return new Diff(type, String.join(" ", sentences));
    }

    private static int whole(Number value) {
        return value == null ? 0 : value.intValue();
    }

    /** The {@code pc.conditions} column: a JSON array of names. Unreadable or absent reads as none. */
    private List<String> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return strings(objectMapper.readValue(json, new TypeReference<List<Object>>() {}));
        } catch (JsonProcessingException e) {
            return List.of();
        }
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
