package com.moo.charactermanagerservice.dto;

/**
 * Body for POST /api/v1/campaign/join — a player consents to seat their PC.
 * {@code acknowledgeVariantRules} must be {@code true} to join a campaign with
 * variant rules enabled (the client shows the conversion consent gate first);
 * older two-field bodies deserialize it as {@code null}, i.e. not acknowledged.
 */
public record JoinCampaignRequest(String code, Long pcId, Boolean acknowledgeVariantRules) {}
