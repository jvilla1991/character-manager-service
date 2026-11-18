package com.moo.charactermanagerservice.dto;

/** DM override of the party split: "APPROVE" (distribute it now) or "CANCEL" (back to a draft). */
public record LootOverrideRequest(String action) {}
