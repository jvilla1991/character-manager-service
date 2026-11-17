package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.*;
import com.moo.charactermanagerservice.models.*;
import com.moo.charactermanagerservice.repositories.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Campaign DM manages the faction tree and scores; players may only read their own scores. */
@Service
public class RenownService {
    private final PCService pcService;
    private final PCRepository pcRepository;
    private final CampaignFactionRepository factions;
    private final PcRenownRepository scores;
    private final PcActivityLogService activityLog;

    public RenownService(PCService pcService, PCRepository pcRepository,
                         CampaignFactionRepository factions, PcRenownRepository scores,
                         PcActivityLogService activityLog) {
        this.pcService = pcService;
        this.pcRepository = pcRepository;
        this.factions = factions;
        this.scores = scores;
        this.activityLog = activityLog;
    }

    @Transactional(readOnly = true)
    public List<RenownView> list(Long pcId, UUID userId) {
        PC pc = pcService.findPCById(pcId);
        if (!userId.equals(pc.getUserId())) pcService.findPCByIdForDm(pcId, userId);
        if (pc.getCampaignId() == null) return List.of();
        Map<Long, Integer> values = scores.findByPcId(pcId).stream()
                .collect(Collectors.toMap(PcRenown::getFactionId, PcRenown::getScore));
        return factions.findByCampaignIdOrderByIdAsc(pc.getCampaignId()).stream()
                .map(f -> view(f, values.getOrDefault(f.getId(), 0))).toList();
    }

    @Transactional
    public RenownView create(Long pcId, FactionRequest request, UUID userId) {
        PC pc = pcService.findPCByIdForDm(pcId, userId);
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faction name must be 1 to 100 characters");
        }
        if (request.parentId() != null) {
            CampaignFaction parent = faction(request.parentId(), pc.getCampaignId());
            if (parent.getParentId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subfactions cannot contain subfactions");
            }
        }
        CampaignFaction faction = new CampaignFaction();
        faction.setCampaignId(pc.getCampaignId());
        faction.setParentId(request.parentId());
        faction.setName(request.name().trim());
        return view(factions.save(faction), 0);
    }

    @Transactional
    public RenownView adjust(Long pcId, Long factionId, RenownAdjustment request, UUID userId) {
        // Lock the PC even when its score row does not exist yet. This serializes first-time
        // and subsequent adjustments, preventing duplicate rows and lost increments.
        pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        PC pc = pcService.findPCByIdForDm(pcId, userId);
        if (request == null || request.delta() == null || (request.delta() != -1 && request.delta() != 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delta must be -1 or 1");
        }
        CampaignFaction faction = faction(factionId, pc.getCampaignId());
        PcRenown score = scores.findByPcIdAndFactionId(pcId, factionId).orElseGet(() -> {
            PcRenown initial = new PcRenown();
            initial.setPcId(pcId);
            initial.setFactionId(factionId);
            return initial;
        });
        int next = Math.max(-100, Math.min(100, score.getScore() + request.delta()));
        if (next != score.getScore()) {
            score.setScore(next);
            scores.save(score);
            activityLog.log(pcId, PcActivityType.DM_EDIT,
                    "Renown with " + faction.getName() + " changed to " + next, userId);
        }
        return view(faction, next);
    }

    private CampaignFaction faction(Long id, Long campaignId) {
        return factions.findById(id).filter(f -> f.getCampaignId().equals(campaignId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Faction not found"));
    }

    private RenownView view(CampaignFaction faction, int score) {
        return new RenownView(faction.getId(), faction.getParentId(), faction.getName(), score);
    }
}
