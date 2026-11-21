package com.wynnweaponviewer.card;

import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.elements.type.Element;
import com.wynntils.models.elements.type.Powder;
import com.wynntils.models.elements.type.Skill;
import com.wynntils.models.gear.type.GearAttackSpeed;
import com.wynntils.models.gear.type.GearRestrictions;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.gear.type.ItemWeightSource;
import com.wynntils.models.stats.type.DamageType;
import com.wynntils.models.stats.type.StatType;
import com.wynntils.models.wynnitem.type.ItemEmblem;
import com.wynntils.utils.type.RangedValue;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, renderer-agnostic snapshot of everything the item card can show.
 * Built from Wynntils' item models by {@link ItemCardDataFactory}; never holds the ItemStack itself.
 */
public record ItemCardData(
        Kind kind,
        String name,
        GearTier tier,
        GearType type,
        ItemEmblem emblem,
        GearRestrictions restriction,
        Optional<String> setName,
        boolean shiny,
        boolean unidentified,
        @Nullable Identifier tooltipStyle,
        List<Element> elements,
        Overall overall,
        Hero hero,
        Requirements requirements,
        List<Identification> identifications,
        Optional<MajorId> majorId,
        Powders powders,
        Optional<ShinyInfo> shinyStat,
        int rerolls,
        List<Scale> scales,
        Optional<String> crafter,
        int wynntilsPage) {

    public enum Kind {
        /** Identified (or pre-identified) gear known to the Wynntils API. */
        GEAR,
        /** Gear known to the API but still unidentified: show ranges, not rolls. */
        GEAR_UNIDENTIFIED,
        /** Unidentified gear box ("Unidentified Spear"): only tier/type/level range are known. */
        GEAR_BOX,
        /** Player-crafted gear: no API entry, percentages come from Wynncraft's own meter. */
        CRAFTED,
        /** Gear parsed from lore but missing from the Wynntils API (e.g. very new items). */
        UNKNOWN
    }

    /** Overall roll quality; {@code percentage} is empty when not applicable (unid, crafted w/o meters, ...). */
    public record Overall(Optional<Float> percentage, boolean estimated, boolean perfect, boolean defective,
                          boolean perfectInternalRolls) {
        public static final Overall NONE = new Overall(Optional.empty(), false, false, false, false);
    }

    /**
     * The headline numbers. Weapons use dps/attackSpeed/damages; armor and accessories use health/defences.
     * {@code fromLiveLore} is true when values were parsed from the item's current lore (includes powders),
     * false when they are the API base values.
     */
    public record Hero(int averageDps, @Nullable GearAttackSpeed attackSpeed, List<Damage> damages,
                       int health, List<Defence> defences, boolean fromLiveLore) {
        public static final Hero NONE = new Hero(0, null, List.of(), 0, List.of(), false);

        public boolean isWeaponStyle() {
            return averageDps > 0 || !damages.isEmpty();
        }
    }

    public record Damage(DamageType type, RangedValue range) {}

    public record Defence(Element element, int value) {}

    /**
     * Requirements. {@code skills} always contains all five skills (0 = no requirement).
     * Met-flags are null when Wynntils couldn't evaluate them (not in a character, unid box, ...).
     */
    public record Requirements(int level, @Nullable RangedValue levelRange, @Nullable ClassType classType,
                               Optional<String> quest, Map<Skill, Integer> skills,
                               @Nullable Boolean levelMet, @Nullable Boolean classMet,
                               @Nullable Map<Skill, Boolean> skillsMet, @Nullable Boolean questMet) {}

    /**
     * One identification line, already ordered per the user's Wynntils ordering.
     *
     * @param value         signed display value (inversion already applied); for unidentified items this is the
     *                      "worst" end of {@code displayRange}
     * @param positive      true if this value is good for the player (green), false if bad (red)
     * @param percentage    roll %, empty when unknown (unid items, crafted items without a meter)
     * @param displayRange  low/high display bounds for unidentified items, null otherwise
     * @param fixed         pre-identified / fixed value (no roll)
     */
    public record Identification(StatType statType, String displayName, int value, String unit, boolean positive,
                                 Optional<Float> percentage, boolean estimated, boolean perfectInternalRoll,
                                 @Nullable RangedValue displayRange, boolean fixed) {}

    public record MajorId(String name, String plainText, List<Component> lines) {}

    /**
     * @param known false until Wynntils has parsed tooltip page 2 (powders/sets); slots are always known
     */
    public record Powders(int slots, List<PowderEntry> applied, boolean known, Optional<String> specialName) {
        public static final Powders NONE = new Powders(0, List.of(), true, Optional.empty());
    }

    public record PowderEntry(Powder powder, int tier) {}

    public record ShinyInfo(String displayName, long value, String unit, int shinyRerolls) {}

    public record Scale(ItemWeightSource source, String name, float percentage) {}
}
