package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * What a prospective member may see for a valid invite code, before joining —
 * enough for the join consent gate (name + variant-rule opt-ins), nothing more.
 */
public record CampaignPreviewView(String name, Map<String, Object> variantRules) {}
