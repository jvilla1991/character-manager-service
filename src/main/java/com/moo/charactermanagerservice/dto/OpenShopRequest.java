package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * DM request to activate a shop in a session. Two forms:
 * <ul>
 *   <li><b>Standard:</b> {@code category} selects the catalog slice
 *       (WEAPON/ARMOR/MATERIAL_COMPONENT) sold at catalog price, unlimited.</li>
 *   <li><b>Curated:</b> {@code curatedShopId} activates a pre-built curated shop
 *       (its own item list + price overrides); {@code category} is ignored.</li>
 * </ul>
 * {@code settlement} is the free-text label the DM types (a curated shop's own
 * settlement is used when this is null); {@code pcIds} are the targeted
 * characters from the session roster.
 */
public record OpenShopRequest(String category, String settlement, List<Long> pcIds, Long curatedShopId) {}
