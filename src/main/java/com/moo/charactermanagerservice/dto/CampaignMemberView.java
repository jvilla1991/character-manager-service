package com.moo.charactermanagerservice.dto;

import com.moo.charactermanagerservice.models.PC;

/**
 * The DM-facing projection of a campaign member. Deliberately excludes a
 * player's private narrative (bio, notes, traits, spells, gear, etc.) — it
 * carries only what the party board / treasury / aggregates need. Field names
 * mirror the PC JSON so the frontend's existing deserializer can read it.
 */
public record CampaignMemberView(
        Long id,
        String name,
        String playerName,
        String clazz,
        Short level,
        String species,
        String subclass,
        Long campaignId,
        String portraitTint,
        String portraitInitials,
        Short abilityStr,
        Short abilityDex,
        Short abilityCon,
        Short abilityInt,
        Short abilityWis,
        Short abilityCha,
        Short profBonus,
        Short hpMax,
        Short hpCurrent,
        Short hpTemp,
        Short ac,
        Short initiative,
        Short speed,
        String skills,
        String conditions,
        String coins
) {
    public static CampaignMemberView from(PC pc) {
        return new CampaignMemberView(
                pc.getId(), pc.getName(), pc.getPlayerName(), pc.getClazz(), pc.getLevel(),
                pc.getSpecies(), pc.getSubclass(), pc.getCampaignId(),
                pc.getPortraitTint(), pc.getPortraitInitials(),
                pc.getAbilityStr(), pc.getAbilityDex(), pc.getAbilityCon(),
                pc.getAbilityInt(), pc.getAbilityWis(), pc.getAbilityCha(),
                pc.getProfBonus(), pc.getHpMax(), pc.getHpCurrent(), pc.getHpTemp(),
                pc.getAc(), pc.getInitiative(), pc.getSpeed(),
                pc.getSkills(), pc.getConditions(), pc.getCoins()
        );
    }
}
