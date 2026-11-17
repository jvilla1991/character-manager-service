package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.ProfessionGrant;
import com.moo.charactermanagerservice.models.*;
import com.moo.charactermanagerservice.repositories.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Separate grants keep generic character saves from overwriting DM decisions. */
@Service
public class PcProfessionService {
    private static final Set<String> KEYS = Set.of("archaeology", "fishing", "herbalism", "mining", "skinning");
    private static final Map<String, String> TOOLS = Map.of("herbalism", "Herbalism Kit",
            "mining", "Mason's Tools", "skinning", "Leatherworker's Tools");
    private final PCService pcs;
    private final PCRepository pcRepository;
    private final PcProfessionRepository grants;
    private final PcActivityLogService log;
    private final ObjectMapper mapper;
    public PcProfessionService(PCService pcs, PCRepository pcRepository, PcProfessionRepository grants,
                               PcActivityLogService log, ObjectMapper mapper) {
        this.pcs = pcs; this.pcRepository = pcRepository; this.grants = grants; this.log = log; this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public List<ProfessionGrant> list(Long pcId, UUID userId) {
        PC pc = pcs.findPCById(pcId);
        if (!userId.equals(pc.getUserId())) pcs.findPCByIdForDm(pcId, userId);
        return grants.findByPcIdOrderByIdAsc(pcId).stream().map(p -> new ProfessionGrant(p.getProfession())).toList();
    }
    @Transactional
    public ProfessionGrant grant(Long pcId, ProfessionGrant request, UUID userId) {
        pcs.findPCByIdForDm(pcId, userId);
        // Serialize grant/revoke operations, including the first insert for a profession.
        PC pc = lock(pcId);
        String key = requireKey(request == null ? null : request.profession());
        if (!eligible(pc, key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required proficiency is missing");
        if (grants.findByPcIdAndProfession(pcId, key).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profession already granted");
        PcProfession grant = new PcProfession(); grant.setPcId(pcId); grant.setProfession(key);
        grants.save(grant);
        log.log(pcId, PcActivityType.DM_EDIT, "DM granted profession: " + key, userId);
        return new ProfessionGrant(key);
    }
    @Transactional
    public void revoke(Long pcId, String profession, UUID userId) {
        pcs.findPCByIdForDm(pcId, userId);
        lock(pcId);
        String key = requireKey(profession);
        PcProfession grant = grants.findByPcIdAndProfession(pcId, key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profession not found"));
        grants.delete(grant);
        log.log(pcId, PcActivityType.DM_EDIT, "DM revoked profession: " + key, userId);
    }
    private PC lock(Long pcId) {
        return pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }
    private String requireKey(String key) {
        if (key == null || !KEYS.contains(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown gathering profession");
        return key;
    }
    private boolean eligible(PC pc, String key) {
        if (key.equals("fishing")) return true;
        if (key.equals("archaeology")) {
            Object proficiency = new PcJsonColumns(mapper).parseObject(pc.getSkills()).get("History");
            return "prof".equals(proficiency) || "expert".equals(proficiency);
        }
        try {
            String[] tools = mapper.readValue(pc.getToolProfs(), String[].class);
            return tools != null && Arrays.stream(tools).filter(Objects::nonNull)
                    .anyMatch(tool -> normalize(tool).equals(normalize(TOOLS.get(key))));
        } catch (java.io.IOException | IllegalArgumentException e) { return false; }
    }
    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'');
    }
}
