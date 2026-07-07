package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * Campaign header visible to the DM and to owners of member PCs — lets a
 * player's character sheet learn the campaign's variant rules and current party
 * location without the DM-only full campaign payload. {@code location} is
 * {@code {name, type}} or null when never set.
 */
public record CampaignSummaryView(Long id, String name, Map<String, Object> variantRules,
                                  Map<String, Object> location) {}
