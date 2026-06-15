package com.moo.charactermanagerservice.dto;

/** Body for POST /api/v1/campaign/join — a player consents to seat their PC. */
public record JoinCampaignRequest(String code, Long pcId) {}
