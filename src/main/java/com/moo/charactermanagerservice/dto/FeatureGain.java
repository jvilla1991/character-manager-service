package com.moo.charactermanagerservice.dto;

/**
 * A single class feature gained at a level — name plus a short description. Surfaced in the
 * level-up preview and recorded among the PC's features on commit. Content is server-owned
 * (unlike feats, whose descriptions are presentation-only), so the description travels with it.
 */
public record FeatureGain(String name, String desc) {}
