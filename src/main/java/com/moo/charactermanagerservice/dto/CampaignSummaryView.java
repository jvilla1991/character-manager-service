package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * Campaign header visible to the DM and to owners of member PCs — lets a
 * player's character sheet learn the campaign's variant rules without the
 * DM-only full campaign payload.
 */
public record CampaignSummaryView(Long id, String name, Map<String, Object> variantRules) {}
