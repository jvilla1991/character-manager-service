package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.FollowerKind;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.progression.VestigeCompanion;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a Vestige Patron's companion in step with its warlock. The companion is a
 * {@code COMPANION} follower the server owns: one step, {@link #sync}, creates it, rebuilds its
 * stat block or removes it, so that after every write of the character the follower is exactly
 * what the character now gives it.
 *
 * <ul>
 *   <li>The character has a vestige ({@link VestigeCompanion#typeFor}: a Vestige Patron warlock
 *       of level 3 or higher with a type chosen) and no companion yet: one is created, named
 *       {@value VestigeCompanion#NAME}, at full Hit Points.</li>
 *   <li>It has one already: the block is built again from the character as it is now, keeping
 *       only what play changed ({@link CompanionVitals#carriedOver}: current Hit Points, raised
 *       by any growth of the maximum; temporary Hit Points; conditions). The name is the
 *       player's and is never touched. A block that comes out the same is not written.</li>
 *   <li>The character has no vestige (the subclass changed, the level dropped below 3, the type
 *       is gone): its vestige companion is removed.</li>
 * </ul>
 *
 * <p>Only followers of kind {@code COMPANION} whose block carries the vestige marker are ever
 * read or written here: a DM's grant, a summon, a familiar or another feature's companion is
 * left exactly as it is.
 *
 * <p>{@link PCService} calls it after a level-up, a whole-sheet save and a subclass-choices
 * write, inside the same transaction. It depends on the follower repository alone, not on
 * {@link PcFollowerService} (which depends on {@link PCService}), so there is no cycle. The
 * caller decides whether a character is worth a look at all: a character that is not, and was
 * not, a Vestige Patron never reaches this class, so its saves cost no query here.
 */
@Component
public class VestigeCompanionSync {

    private final PcFollowerRepository followerRepository;
    private final ObjectMapper objectMapper;
    private final PcJsonColumns json;

    @Autowired
    public VestigeCompanionSync(PcFollowerRepository followerRepository, ObjectMapper objectMapper) {
        this.followerRepository = followerRepository;
        this.objectMapper = objectMapper;
        this.json = new PcJsonColumns(objectMapper);
    }

    /**
     * Bring the character's vestige companion in line with the character as saved.
     *
     * @return true when a follower was created, rewritten or removed, so the caller can wake the
     *         character's live session (a follower rides in its owner's snapshot row)
     */
    public boolean sync(PC pc) {
        if (pc == null || pc.getId() == null) return false;
        List<PcFollower> vestiges = followerRepository.findByPcIdAndKind(pc.getId(), FollowerKind.COMPANION)
                .stream()
                .filter(follower -> VestigeCompanion.isVestige(json.parseObject(follower.getStatBlock())))
                .toList();

        int level = pc.getLevel() == null ? 1 : pc.getLevel();
        String type = VestigeCompanion.typeFor(pc.getClazz(), pc.getSubclass(), level, pc.getSubclassChoices());
        if (type == null) {
            if (vestiges.isEmpty()) return false;
            followerRepository.deleteAll(vestiges);
            return true;
        }

        Map<String, Object> fresh = VestigeCompanion.statBlock(type, level, charisma(pc),
                LevelUpService.proficiencyBonus(pc, level), languages(pc));
        if (vestiges.isEmpty()) {
            PcFollower vestige = new PcFollower();
            vestige.setPcId(pc.getId());
            vestige.setName(VestigeCompanion.NAME);
            vestige.setStatBlock(json.writeObject(fresh));
            // The column is NOT NULL; a sheet saved without its owner must not fail over its vestige.
            vestige.setGrantedByUserId(pc.getUserId() != null ? pc.getUserId() : new UUID(0L, 0L));
            vestige.setKind(FollowerKind.COMPANION);
            followerRepository.save(vestige);
            return true;
        }

        boolean changed = false;
        if (vestiges.size() > 1) {
            // There is one vestige. Should a second ever appear, the first one stays.
            followerRepository.deleteAll(vestiges.subList(1, vestiges.size()));
            changed = true;
        }
        PcFollower vestige = vestiges.get(0);
        Map<String, Object> stored = json.parseObject(vestige.getStatBlock());
        Map<String, Object> next = CompanionVitals.carriedOver(fresh, stored);
        if (!next.equals(stored)) {
            vestige.setStatBlock(json.writeObject(next));
            followerRepository.save(vestige);
            changed = true;
        }
        return changed;
    }

    /** The warlock's Charisma score; a sheet that never stored one counts as 10. */
    private static int charisma(PC pc) {
        return pc.getAbilityCha() == null ? 10 : pc.getAbilityCha();
    }

    /** The languages on the character's sheet (a JSON array of names); none when unreadable. */
    private List<String> languages(PC pc) {
        List<String> names = new ArrayList<>();
        String stored = pc.getLanguages();
        if (stored == null || stored.isBlank()) return names;
        try {
            List<Object> parsed = objectMapper.readValue(stored, new TypeReference<>() {});
            if (parsed == null) return names;
            for (Object entry : parsed) {
                if (entry instanceof String name && !name.isBlank()) names.add(name.trim());
            }
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
        return names;
    }
}
