package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Cleric's 2024 Player's Handbook subclasses (plus the Twilight Domain
 * from Tasha's Cauldron, adapted to the 2024 level structure, and the Arcana Domain from Arcana
 * Unleashed), keyed by the exact subclass name in ClassProgression's subclass catalog, then by
 * the Cleric level at which each feature is gained. Descriptions are concise summaries, not
 * rules text.
 */
final class ClericSubclasses {

    private ClericSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put(SubclassChoices.ARCANA_DOMAIN, arcana());
        m.put("Life Domain", life());
        m.put("Light Domain", light());
        m.put("Trickery Domain", trickery());
        m.put("Twilight Domain", twilight());
        m.put("War Domain", war());
        return m;
    }

    /**
     * Arcana Domain (Arcana Unleashed). Its skill and its Wizard spells are choices the player
     * makes: see {@link SubclassChoices}. The domain spell list is read from the text below by
     * {@link ClassSpellGrants}.
     */
    private static Map<Integer, List<FeatureGain>> arcana() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Arcana Domain Spells",
                        "You always have these spells prepared. "
                                + "3rd — Detect Magic, Magic Missile, Magic Weapon, Nystul's Magic Aura; "
                                + "5th — Counterspell, Dispel Magic; "
                                + "7th — Arcane Eye, Leomund's Secret Chest; "
                                + "9th — Bigby's Hand, Teleportation Circle."),
                f("Modify Magic",
                        "When you cast a spell, you can spend a Channel Divinity use (no action) to "
                                + "change it in one way. Fortifying Spell: one target gains Temporary "
                                + "Hit Points equal to 2d8 + your Cleric level. Tenacious Spell: when "
                                + "a creature you can see succeeds on a saving throw the spell forces, "
                                + "subtract 1d6 from its first saving throw against the spell."),
                f(SubclassChoices.STUDENT_OF_ARCANA,
                        "You gain proficiency in Arcana or in one skill from the Cleric's level 1 "
                                + "skill list. You also learn two Wizard cantrips; each time you gain "
                                + "a Cleric level, you can swap one of them for another Wizard "
                                + "cantrip.")));
        f.put(6, List.of(
                f("Dispelling Recovery",
                        "Right after you cast a spell with a spell slot that restores Hit Points to a "
                                + "creature or ends a condition on one, you can cast Dispel Magic as "
                                + "part of the same action without a spell slot. Once per Short or "
                                + "Long Rest, or spend a Channel Divinity use to regain the use.")));
        f.put(17, List.of(
                f(SubclassChoices.MAGICAL_MASTERY,
                        "You learn four Wizard spells, one each of levels 6, 7, 8 and 9, and you "
                                + "always have them prepared. Each time you gain a Cleric level, you "
                                + "can swap one of them for another Wizard spell of the same "
                                + "level.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> life() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Life Domain Spells",
                        "You always have these spells prepared. "
                                + "3rd — Aid, Bless, Cure Wounds, Lesser Restoration; "
                                + "5th — Mass Healing Word, Revivify; "
                                + "7th — Aura of Life, Death Ward; "
                                + "9th — Greater Restoration, Mass Cure Wounds."),
                f("Disciple of Life",
                        "When a spell you cast with a spell slot restores Hit Points to a creature, "
                                + "that creature regains extra Hit Points equal to 2 + the slot's "
                                + "level on the turn you cast it."),
                f("Preserve Life",
                        "As a Magic action, spend a Channel Divinity use to restore Hit Points equal "
                                + "to five times your Cleric level, divided among Bloodied creatures "
                                + "within 30 feet (you included). It can't raise a creature above "
                                + "half its Hit Point maximum.")));
        f.put(6, List.of(
                f("Blessed Healer",
                        "When you cast a spell with a spell slot that restores Hit Points to a "
                                + "creature other than you, you also regain Hit Points equal to 2 + "
                                + "the slot's level.")));
        f.put(17, List.of(
                f("Supreme Healing",
                        "When you would roll dice to restore Hit Points with a spell or Channel "
                                + "Divinity, you don't roll; each die counts as its highest possible "
                                + "number.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> light() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Light Domain Spells",
                        "You always have these spells prepared. "
                                + "3rd — Burning Hands, Faerie Fire, Scorching Ray, See Invisibility; "
                                + "5th — Daylight, Fireball; "
                                + "7th — Arcane Eye, Wall of Fire; "
                                + "9th — Flame Strike, Scrying."),
                f("Radiance of the Dawn",
                        "As a Magic action, spend a Channel Divinity use to emit a 30-foot Emanation "
                                + "that dispels magical Darkness. Creatures of your choice in it make "
                                + "a Constitution save, taking 2d10 + your Cleric level Radiant "
                                + "damage, or half on a success."),
                f("Warding Flare",
                        "When a creature you can see within 30 feet makes an attack roll, you can use "
                                + "your Reaction to impose Disadvantage on it. You can do this a "
                                + "number of times equal to your Wisdom modifier (minimum 1) per "
                                + "Long Rest.")));
        f.put(6, List.of(
                f("Improved Warding Flare",
                        "You regain all uses of Warding Flare on a Short or Long Rest. When you use "
                                + "it, you can also give the target of the attack Temporary Hit "
                                + "Points equal to 2d6 + your Wisdom modifier.")));
        f.put(17, List.of(
                f("Corona of Light",
                        "As a Magic action, shed Bright Light for 60 feet and Dim Light for 30 more "
                                + "for 1 minute. Enemies in the Bright Light have Disadvantage on "
                                + "saves against Radiance of the Dawn and spells dealing Fire or "
                                + "Radiant damage. Uses equal your Wisdom modifier per Long Rest.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> trickery() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Trickery Domain Spells",
                        "You always have these spells prepared. "
                                + "3rd — Charm Person, Disguise Self, Invisibility, "
                                + "Pass without Trace; "
                                + "5th — Hypnotic Pattern, Nondetection; "
                                + "7th — Confusion, Dimension Door; "
                                + "9th — Dominate Person, Modify Memory."),
                f("Blessing of the Trickster",
                        "As a Magic action, give yourself or a willing creature within 30 feet "
                                + "Advantage on Dexterity (Stealth) checks. It lasts until you finish "
                                + "a Long Rest or use this feature again."),
                f("Invoke Duplicity",
                        "As a Bonus Action, spend a Channel Divinity use to create an illusion of "
                                + "yourself within 30 feet for 1 minute. You can cast spells from its "
                                + "space, gain Advantage on attacks against a creature within 5 feet "
                                + "of both of you, and move it 30 feet as a Bonus Action.")));
        f.put(6, List.of(
                f("Trickster's Transposition",
                        "Whenever you use the Bonus Action to create or move your Invoke Duplicity "
                                + "illusion, you can teleport, swapping places with it.")));
        f.put(17, List.of(
                f("Improved Duplicity",
                        "You and your allies have Advantage on attack rolls against creatures within "
                                + "5 feet of your illusion. When the illusion ends, you or a creature "
                                + "within 5 feet of it regains Hit Points equal to your Cleric "
                                + "level.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> twilight() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Twilight Domain Spells",
                        "You always have these spells prepared. "
                                + "3rd — Faerie Fire, Moonbeam, See Invisibility, Sleep; "
                                + "5th — Aura of Vitality, Leomund's Tiny Hut; "
                                + "7th — Aura of Life, Greater Invisibility; "
                                + "9th — Circle of Power, Mirage Arcane."),
                f("Eyes of Night",
                        "You have Darkvision out to 300 feet. As a Magic action, you can share it "
                                + "with willing creatures within 10 feet, up to your Wisdom modifier "
                                + "(minimum 1), for 1 hour. Once per Long Rest, or spend a spell slot "
                                + "of any level to do it again."),
                f("Vigilant Blessing",
                        "As a Magic action, touch one creature (it can be you) to give it Advantage "
                                + "on its next Initiative roll. The benefit ends after that roll or "
                                + "when you use this feature again."),
                f("Channel Divinity: Twilight Sanctuary",
                        "As a Magic action, spend a Channel Divinity use to surround yourself with a "
                                + "30-foot sphere of Dim Light for 1 minute. When a creature ends its "
                                + "turn in it, you can grant it 1d6 + your Cleric level Temporary Hit "
                                + "Points or end one effect Charming or Frightening it.")));
        f.put(6, List.of(
                f("Steps of Night",
                        "As a Bonus Action while in Dim Light or Darkness, you gain a Fly Speed equal "
                                + "to your Speed for 1 minute. You can do this a number of times "
                                + "equal to your Proficiency Bonus per Long Rest.")));
        f.put(17, List.of(
                f("Twilight Shroud",
                        "You and your allies have Half Cover while inside the sphere created by your "
                                + "Twilight Sanctuary.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> war() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("War Domain Spells",
                        "You always have these spells prepared. "
                                + "3rd — Guiding Bolt, Magic Weapon, Shield of Faith, "
                                + "Spiritual Weapon; "
                                + "5th — Crusader's Mantle, Spirit Guardians; "
                                + "7th — Fire Shield, Freedom of Movement; "
                                + "9th — Hold Monster, Steel Wind Strike."),
                f("Guided Strike",
                        "When you or a creature within 30 feet of you misses with an attack roll, you "
                                + "can spend a Channel Divinity use to add +10 to the roll. Helping "
                                + "another creature's attack this way takes your Reaction."),
                f("War Priest",
                        "As a Bonus Action, you can make one attack with a weapon or an Unarmed "
                                + "Strike. You can do this a number of times equal to your Wisdom "
                                + "modifier (minimum 1) and regain all uses on a Short or Long "
                                + "Rest.")));
        f.put(6, List.of(
                f("War God's Blessing",
                        "You can spend a Channel Divinity use instead of a spell slot to cast Shield "
                                + "of Faith or Spiritual Weapon. Cast this way, the spell needs no "
                                + "Concentration and lasts 1 minute, ending early if you cast it "
                                + "again or are Incapacitated.")));
        f.put(17, List.of(
                f("Avatar of Battle",
                        "You have Resistance to Bludgeoning, Piercing, and Slashing damage.")));
        return f;
    }
}
