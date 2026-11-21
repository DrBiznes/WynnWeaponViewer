package com.wynnweaponviewer.card;

import com.wynnweaponviewer.WynnWeaponViewer;
import com.wynntils.core.components.Handlers;
import com.wynntils.core.components.Models;
import com.wynntils.core.components.Services;
import com.wynntils.handlers.tooltip.type.TooltipOptions;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.elements.type.Element;
import com.wynntils.models.elements.type.Powder;
import com.wynntils.models.elements.type.Skill;
import com.wynntils.models.gear.type.GearAttackSpeed;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.gear.type.GearInstanceRequirements;
import com.wynntils.models.gear.type.GearMajorId;
import com.wynntils.models.gear.type.GearRequirements;
import com.wynntils.models.gear.type.GearRestrictions;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.gear.type.ItemWeightSource;
import com.wynntils.models.items.items.game.CraftedGearItem;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.items.game.UnknownGearItem;
import com.wynntils.models.stats.StatCalculator;
import com.wynntils.models.stats.type.DamageType;
import com.wynntils.models.stats.type.FixedStats;
import com.wynntils.models.stats.type.ShinyStat;
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.models.stats.type.StatPossibleValues;
import com.wynntils.models.stats.type.StatType;
import com.wynntils.models.wynnitem.parsing.WynnItemParser;
import com.wynntils.models.wynnitem.parsing.WynnItemStatsParseResult;
import com.wynntils.models.wynnitem.type.ItemEmblem;
import com.wynntils.services.itemweight.type.ItemWeighting;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.type.RangedValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a Wynntils-annotated ItemStack into an {@link ItemCardData} snapshot.
 *
 * <p>Must be given the <b>live</b> stack (slot or carried item): {@code ItemStack.copy()} drops the Wynntils
 * annotation, so a copy will always resolve to {@link Optional#empty()}.
 */
public final class ItemCardDataFactory {

    /** Wynntils' fallback when an item has no emblem of its own. */
    private static final ItemEmblem DEFAULT_EMBLEM = new ItemEmblem(ItemEmblem.ItemEmblemShape.DIAMOND, 1);

    private ItemCardDataFactory() {}

    public static Optional<ItemCardData> create(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        try {
            Optional<GearItem> gear = Models.Item.asWynnItem(stack, GearItem.class);
            if (gear.isPresent()) return Optional.of(fromGear(stack, gear.get()));

            Optional<CraftedGearItem> crafted = Models.Item.asWynnItem(stack, CraftedGearItem.class);
            if (crafted.isPresent()) return Optional.of(fromCrafted(stack, crafted.get()));

            Optional<UnknownGearItem> unknown = Models.Item.asWynnItem(stack, UnknownGearItem.class);
            if (unknown.isPresent()) return Optional.of(fromUnknown(stack, unknown.get()));

            Optional<GearBoxItem> box = Models.Item.asWynnItem(stack, GearBoxItem.class);
            if (box.isPresent()) return Optional.of(fromGearBox(stack, box.get()));
        } catch (RuntimeException e) {
            // Wynntils internals can throw on half-parsed items; never take the screen down with us.
            WynnWeaponViewer.LOGGER.warn("Failed to build item card data for {}", stack.getHoverName().getString(), e);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Item kinds
    // ------------------------------------------------------------------------------------------------------------

    private static ItemCardData fromGear(ItemStack stack, GearItem item) {
        GearInfo info = item.getItemInfo(); // re-read every time: ascended mythics swap GearInfo at runtime
        GearTier tier = info.tier();
        boolean unidentified = item.isUnidentified();
        boolean shiny = item.getShinyStat().isPresent();
        FixedStats fixed = info.fixedStats();

        ItemCardData.Hero hero = heroFromLiveLore(stack, item, info)
                .orElseGet(() -> new ItemCardData.Hero(
                        fixed.averageDps(),
                        fixed.attackSpeed().orElse(null),
                        damages(fixed.damages()),
                        fixed.healthBuff(),
                        defences(fixed.defences()),
                        false));

        Optional<ItemCardData.MajorId> majorId = fixed.majorIds().map(ItemCardDataFactory::majorId);

        List<ItemCardData.Scale> scales = unidentified ? List.of() : scales(item);

        return new ItemCardData(
                unidentified ? ItemCardData.Kind.GEAR_UNIDENTIFIED : ItemCardData.Kind.GEAR,
                item.getName(),
                tier,
                item.getGearType(),
                info.emblem() != null ? info.emblem() : DEFAULT_EMBLEM,
                restriction(info.metaInfo() == null ? null : info.metaInfo().restrictions()),
                info.setInfo().map(set -> set.cleanName()),
                shiny,
                unidentified,
                tooltipStyle(stack, tier, shiny),
                List.copyOf(info.elements()),
                unidentified ? ItemCardData.Overall.NONE : overall(item.hasOverallValue(), item.getOverallPercentage(),
                        item.hasEstimatedOverallPercentage(), item.isPerfect(), item.isDefective(),
                        item.hasFullPerfectInternalRolls()),
                hero,
                requirements(info.requirements(), null, item.getGearInstanceRequirements()),
                identifications(item.getIdentifications(), item.getPossibleValues(), unidentified,
                        info.requirements().classType().orElse(null), item.getIdentificationLevelRange()),
                majorId,
                new ItemCardData.Powders(item.getPowderSlots(), powders(item.getPowders()),
                        item.hasParsedPage(1) || !item.getPowders().isEmpty(),
                        item.getPowderSpecial().map(p -> p.name())),
                item.getShinyStat().map(ItemCardDataFactory::shinyInfo),
                item.getRerollCount(),
                scales,
                Optional.empty(),
                item.currentPage());
    }

    private static ItemCardData fromCrafted(ItemStack stack, CraftedGearItem item) {
        GearTier tier = item.getGearTier();
        List<ItemCardData.Damage> damages = damages(item.getDamages());
        List<ItemCardData.Defence> defences = defences(item.getDefences());
        GearRequirements reqs = item.getRequirements();

        return new ItemCardData(
                ItemCardData.Kind.CRAFTED,
                item.getName(),
                tier,
                item.getGearType(),
                ItemEmblem.fromCraftedItem(item.getGearType()),
                GearRestrictions.NONE,
                Optional.empty(),
                false,
                false,
                tooltipStyle(stack, tier, false),
                elementsFrom(damages, defences),
                craftedOverall(item.getIdentifications()),
                new ItemCardData.Hero(item.getDps(), item.getAttackSpeed().orElse(null), damages,
                        item.getHealth(), defences, true),
                requirements(reqs, null, item.getGearInstanceRequirements()),
                identifications(item.getIdentifications(), List.of(), false,
                        reqs == null ? null : reqs.classType().orElse(null), RangedValue.NONE),
                Optional.empty(),
                new ItemCardData.Powders(item.getPowderSlots(), powders(item.getPowders()),
                        item.hasParsedPage(1) || !item.getPowders().isEmpty(),
                        item.getPowderSpecial().map(p -> p.name())),
                Optional.empty(),
                0,
                List.of(),
                Optional.ofNullable(item.getCreator()).filter(s -> !s.isBlank()),
                item.currentPage());
    }

    private static ItemCardData fromUnknown(ItemStack stack, UnknownGearItem item) {
        GearTier tier = item.getGearTier();
        boolean shiny = item.getShinyStat().isPresent();
        boolean unidentified = item.isUnidentified();
        List<ItemCardData.Damage> damages = damages(item.getDamages());
        List<ItemCardData.Defence> defences = defences(item.getDefences());
        GearRequirements reqs = item.getRequirements();

        Optional<ItemCardData.MajorId> majorId = Optional.empty();
        List<Component> majorLines = item.getMajorIdLines();
        if (majorLines != null && !majorLines.isEmpty()) {
            StringBuilder plain = new StringBuilder();
            for (Component line : majorLines) {
                if (!plain.isEmpty()) plain.append(' ');
                plain.append(line.getString().strip());
            }
            majorId = Optional.of(new ItemCardData.MajorId("", plain.toString(), List.copyOf(majorLines)));
        }

        return new ItemCardData(
                unidentified ? ItemCardData.Kind.GEAR_UNIDENTIFIED : ItemCardData.Kind.UNKNOWN,
                item.getName(),
                tier,
                item.getGearType(),
                DEFAULT_EMBLEM,
                restriction(item.getRestrictions()),
                Optional.empty(),
                shiny,
                unidentified,
                tooltipStyle(stack, tier, shiny),
                elementsFrom(damages, defences),
                unidentified ? ItemCardData.Overall.NONE : overall(item.hasOverallValue(), item.getOverallPercentage(),
                        item.hasEstimatedOverallPercentage(), item.isPerfect(), item.isDefective(),
                        item.hasFullPerfectInternalRolls()),
                new ItemCardData.Hero(0, item.getAttackSpeed(), damages, item.getHealth(), defences, true),
                requirements(reqs, null, item.getGearInstanceRequirements()),
                identifications(item.getIdentifications(), item.getPossibleValues(), unidentified,
                        reqs == null ? null : reqs.classType().orElse(null), item.getIdentificationLevelRange()),
                majorId,
                new ItemCardData.Powders(item.getPowderSlots(), powders(item.getPowders()),
                        item.hasParsedPage(1) || !item.getPowders().isEmpty(),
                        item.getPowderSpecial().map(p -> p.name())),
                item.getShinyStat().map(ItemCardDataFactory::shinyInfo),
                item.getRerollCount(),
                List.of(),
                Optional.empty(),
                item.currentPage());
    }

    private static ItemCardData fromGearBox(ItemStack stack, GearBoxItem item) {
        GearTier tier = item.getGearTier();
        GearType type = item.getGearType();
        RangedValue levelRange = item.getLevelRange();
        Map<Skill, Integer> noSkills = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) noSkills.put(skill, 0);

        return new ItemCardData(
                ItemCardData.Kind.GEAR_BOX,
                "Unidentified " + prettyTypeName(type),
                tier,
                type,
                DEFAULT_EMBLEM,
                GearRestrictions.NONE,
                Optional.empty(),
                false,
                true,
                tooltipStyle(stack, tier, false),
                List.of(),
                ItemCardData.Overall.NONE,
                ItemCardData.Hero.NONE,
                new ItemCardData.Requirements(levelRange.low(), levelRange, null, Optional.empty(), Map.copyOf(noSkills),
                        null, null, null, null),
                List.of(),
                Optional.empty(),
                ItemCardData.Powders.NONE,
                Optional.empty(),
                0,
                List.of(),
                Optional.empty(),
                0);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Pieces
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Wynncraft's own lore already includes powders in DPS / damages / defences, while {@code GearInfo.fixedStats()}
     * holds API base values. The stats live on tooltip page 0, so only parse when that's the page being shown.
     */
    private static Optional<ItemCardData.Hero> heroFromLiveLore(ItemStack stack, GearItem item, GearInfo info) {
        if (item.currentPage() != 0) return Optional.empty();
        try {
            WynnItemStatsParseResult parsed = WynnItemParser.parseItemStackStatsPage(stack, info.getVariableStatsMap());
            boolean hasWeaponStats = parsed.dps() > 0 || !parsed.damages().isEmpty();
            boolean hasArmorStats = parsed.health() != 0 || !parsed.defences().isEmpty();
            if (!hasWeaponStats && !hasArmorStats) return Optional.empty();

            GearAttackSpeed speed = parsed.attackSpeed() != null
                    ? parsed.attackSpeed()
                    : info.fixedStats().attackSpeed().orElse(null);
            return Optional.of(new ItemCardData.Hero(
                    parsed.dps() > 0 ? parsed.dps() : info.fixedStats().averageDps(),
                    speed,
                    damages(parsed.damages()),
                    parsed.health(),
                    defences(parsed.defences()),
                    true));
        } catch (RuntimeException e) {
            WynnWeaponViewer.LOGGER.debug("Live lore stat parse failed for {}, using API values", item.getName(), e);
            return Optional.empty();
        }
    }

    private static ItemCardData.Overall overall(boolean has, float pct, boolean estimated, boolean perfect,
                                                boolean defective, boolean perfectInternal) {
        if (!has) return ItemCardData.Overall.NONE;
        return new ItemCardData.Overall(Optional.of(pct), estimated, perfect, defective, perfectInternal);
    }

    /** Crafted items have no API ranges; average Wynncraft's own roll meters when present. */
    private static ItemCardData.Overall craftedOverall(List<StatActualValue> ids) {
        List<StatActualValue> metered = ids.stream().filter(v -> v.vanillaMeter() != CappedValue.EMPTY).toList();
        if (metered.isEmpty()) return ItemCardData.Overall.NONE;
        double avg = metered.stream().mapToDouble(v -> v.vanillaMeter().getPercentage()).average().orElse(0);
        return new ItemCardData.Overall(Optional.of((float) avg), true, avg >= 100, avg <= 0, false);
    }

    private static ItemCardData.Requirements requirements(@Nullable GearRequirements reqs,
                                                          @Nullable RangedValue levelRange,
                                                          @Nullable GearInstanceRequirements met) {
        Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) skills.put(skill, 0);
        if (reqs == null) {
            return new ItemCardData.Requirements(0, levelRange, null, Optional.empty(), Map.copyOf(skills),
                    null, null, null, null);
        }
        for (Pair<Skill, Integer> pair : reqs.skills()) skills.put(pair.a(), pair.b());

        Map<Skill, Boolean> skillsMet = null;
        if (met != null && met.skillReqsMet() != null) {
            skillsMet = new EnumMap<>(Skill.class);
            skillsMet.putAll(met.skillReqsMet());
            skillsMet = Map.copyOf(skillsMet);
        }
        return new ItemCardData.Requirements(
                reqs.level(),
                levelRange,
                reqs.classType().orElse(null),
                reqs.quest(),
                Map.copyOf(skills),
                met == null ? null : met.levelReqMet(),
                met == null ? null : met.classReqMet(),
                skillsMet,
                met == null ? null : met.questReqMet());
    }

    /**
     * Identified items: one entry per rolled ID. Unidentified items: one entry per possible ID with its display range.
     * Sorted using the user's Wynntils identification ordering.
     */
    private static List<ItemCardData.Identification> identifications(List<StatActualValue> actuals,
                                                                     List<StatPossibleValues> possibles,
                                                                     boolean unidentified,
                                                                     @Nullable ClassType classReq,
                                                                     RangedValue levelRange) {
        TooltipOptions options = Handlers.Tooltip.getTooltipOptions();
        List<ItemCardData.Identification> out = new ArrayList<>();

        if (unidentified) {
            for (StatPossibleValues possible : possibles) {
                StatType statType = possible.statType();
                Pair<Integer, Integer> range = StatCalculator.getDisplayRange(possible, options.showBestValueLastAlways());
                int first = range.a();
                int last = range.b();
                boolean positive = (first > 0) ^ statType.displayAsInverted();
                boolean fixed = possible.range().isFixed() || possible.isPreIdentified();
                out.add(new ItemCardData.Identification(statType, statDisplayName(statType, classReq, levelRange),
                        first, statType.getUnit().getDisplayName(), positive, Optional.empty(), false, false,
                        new RangedValue(Math.min(first, last), Math.max(first, last)), fixed));
            }
        } else {
            for (StatActualValue actual : actuals) {
                StatType statType = actual.statType();
                StatPossibleValues possible = possibles.stream()
                        .filter(p -> p.statType().equals(statType))
                        .findFirst()
                        .orElse(null);

                int value = statType.calculateAsInverted() ? -actual.value() : actual.value();
                boolean positive = (value > 0) ^ statType.displayAsInverted();
                boolean fixed = possible != null && (possible.range().isFixed() || possible.isPreIdentified());

                Optional<Float> pct = Optional.empty();
                boolean estimated = false;
                boolean canCalculate = (possible != null && !fixed) || actual.vanillaMeter() != CappedValue.EMPTY;
                if (canCalculate) {
                    StatCalculator.PercentageCalculation calc = StatCalculator.calculatePercentage(actual, possible);
                    pct = Optional.of(calc.value());
                    estimated = calc.estimated();
                }

                out.add(new ItemCardData.Identification(statType, statDisplayName(statType, classReq, levelRange),
                        value, statType.getUnit().getDisplayName(), positive, pct, estimated,
                        actual.perfectInternalRoll(), null, fixed));
            }
        }

        List<StatType> ordering = Models.Stat.getOrderingList(options.identificationOrdering());
        if (ordering != null && !ordering.isEmpty()) {
            out.sort(Comparator.comparingInt(id -> {
                int idx = ordering.indexOf(id.statType());
                return idx < 0 ? Integer.MAX_VALUE : idx;
            }));
        }
        return List.copyOf(out);
    }

    private static String statDisplayName(StatType statType, @Nullable ClassType classReq, RangedValue levelRange) {
        try {
            return Models.Stat.getDisplayName(statType, classReq, Models.Character.getClassType(), levelRange);
        } catch (RuntimeException e) {
            // Spell-cost names need a class; outside a character there may be none.
            return statType.getDisplayName();
        }
    }

    /** Nori / Wynnpool scales, honouring the user's Wynntils weight-source setting. */
    private static List<ItemCardData.Scale> scales(GearItem item) {
        ItemWeightSource setting = Handlers.Tooltip.getTooltipOptions().itemWeightSource();
        List<ItemWeightSource> sources = switch (setting) {
            case NONE -> List.of();
            case ALL -> List.of(ItemWeightSource.NORI, ItemWeightSource.WYNNPOOL);
            default -> List.of(setting);
        };

        List<ItemCardData.Scale> out = new ArrayList<>();
        for (ItemWeightSource source : sources) {
            List<ItemWeighting> weightings = Services.ItemWeight.getItemWeighting(item.getName(), source);
            for (ItemWeighting weighting : weightings) {
                float pct = Services.ItemWeight.calculateWeighting(weighting, item);
                out.add(new ItemCardData.Scale(source, weighting.weightName(), pct));
            }
        }
        return List.copyOf(out);
    }

    private static ItemCardData.MajorId majorId(GearMajorId major) {
        String plain = major.lore() == null ? "" : major.lore().getStringWithoutFormatting().strip();
        List<Component> lines = major.lore() == null ? List.of() : List.of(major.lore().getComponent());
        return new ItemCardData.MajorId(major.name(), plain, lines);
    }

    private static ItemCardData.ShinyInfo shinyInfo(ShinyStat shiny) {
        return new ItemCardData.ShinyInfo(shiny.statType().displayName(), shiny.value(),
                shiny.statType().statUnit().getDisplayName(), shiny.shinyRerolls());
    }

    private static List<ItemCardData.Damage> damages(@Nullable List<Pair<DamageType, RangedValue>> damages) {
        if (damages == null) return List.of();
        return damages.stream().map(p -> new ItemCardData.Damage(p.a(), p.b())).toList();
    }

    private static List<ItemCardData.Defence> defences(@Nullable List<Pair<Element, Integer>> defences) {
        if (defences == null) return List.of();
        return defences.stream().map(p -> new ItemCardData.Defence(p.a(), p.b())).toList();
    }

    private static List<ItemCardData.PowderEntry> powders(@Nullable List<Pair<Powder, Integer>> powders) {
        if (powders == null) return List.of();
        return powders.stream().map(p -> new ItemCardData.PowderEntry(p.a(), p.b())).toList();
    }

    /** Elements for items without an API entry: whichever elements deal damage or grant defence. */
    private static List<Element> elementsFrom(List<ItemCardData.Damage> damages, List<ItemCardData.Defence> defences) {
        Set<Element> set = new LinkedHashSet<>();
        for (ItemCardData.Damage d : damages) d.type().getElement().ifPresent(set::add);
        for (ItemCardData.Defence d : defences) set.add(d.element());
        return List.copyOf(set);
    }

    /** The server sets {@code minecraft:tooltip_style}; fall back to Wynntils' tier mapping if it's missing. */
    @Nullable
    private static Identifier tooltipStyle(ItemStack stack, GearTier tier, boolean shiny) {
        Identifier style = stack.get(DataComponents.TOOLTIP_STYLE);
        if (style != null) return style;
        try {
            return tier.getTooltipStyle(shiny);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static GearRestrictions restriction(@Nullable GearRestrictions restriction) {
        return restriction == null ? GearRestrictions.NONE : restriction;
    }

    private static String prettyTypeName(GearType type) {
        String name = type.name().toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
