package com.moo.charactermanagerservice.dto;

/**
 * One campaign faction with no character's score attached — what the DM's
 * session reward bar lists in its faction picker. {@code parentId} is null for
 * a top-level faction and the parent's id for a subfaction.
 */
public record FactionView(Long id, Long parentId, String name) {}
