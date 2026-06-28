package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * DM request to activate a shop in a session. {@code category} selects the
 * catalog slice (WEAPON for Phase 1); {@code settlement} is the free-text label
 * the DM types; {@code pcIds} are the characters (from the session roster)
 * placed at the shop.
 */
public record OpenShopRequest(String category, String settlement, List<Long> pcIds) {}
