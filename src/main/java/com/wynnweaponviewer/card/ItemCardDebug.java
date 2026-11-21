package com.wynnweaponviewer.card;

import com.wynntils.models.elements.type.Element;
import com.wynntils.models.elements.type.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Human-readable dump of an {@link ItemCardData}, used by {@code /wwb debug card}. */
public final class ItemCardDebug {

    private ItemCardDebug() {}

    public static List<String> describe(ItemCardData d) {
        List<String> out = new ArrayList<>();

        out.add("§e" + d.name() + "§7  [" + d.kind() + "]  tier=" + d.tier() + " type=" + d.type()
                + (d.shiny() ? " §bshiny§7" : "") + (d.unidentified() ? " §cunid§7" : ""));
        out.add("§7emblem=" + d.emblem().itemEmblemShape() + "_" + d.emblem().variant()
                + "  restriction=" + d.restriction() + d.setName().map(s -> "  set=" + s).orElse(""));
        out.add("§7style=" + d.tooltipStyle() + "  page=" + d.wynntilsPage()
                + "  elements=" + d.elements().stream().map(Element::name).collect(Collectors.joining(",")));

        ItemCardData.Overall o = d.overall();
        out.add("§7overall=" + o.percentage().map(p -> fmt(p) + "%" + (o.estimated() ? "~" : "")).orElse("—")
                + (o.perfect() ? " PERFECT" : "") + (o.defective() ? " DEFECTIVE" : "")
                + (o.perfectInternalRolls() ? " (perfect internal)" : "")
                + "  rerolls=" + d.rerolls());

        ItemCardData.Hero h = d.hero();
        if (h.isWeaponStyle()) {
            out.add("§6DPS " + h.averageDps() + "§7  speed="
                    + (h.attackSpeed() == null ? "—" : h.attackSpeed().getName() + " (" + h.attackSpeed().getHitsPerSecond() + " hits/s)")
                    + "  " + (h.fromLiveLore() ? "live lore" : "API base"));
            out.add("§7dmg: " + h.damages().stream()
                    .map(dm -> dm.type().name() + " " + dm.range().low() + "-" + dm.range().high())
                    .collect(Collectors.joining(", ")));
        }
        if (h.health() != 0 || !h.defences().isEmpty()) {
            out.add("§cHealth " + h.health() + "§7  def: " + h.defences().stream()
                    .map(df -> df.element().name() + " " + df.value())
                    .collect(Collectors.joining(", ")));
        }

        ItemCardData.Requirements r = d.requirements();
        out.add("§7req: lv " + (r.levelRange() != null ? r.levelRange().low() + "-" + r.levelRange().high() : r.level())
                + met(r.levelMet())
                + (r.classType() != null ? "  class " + r.classType().getName() + met(r.classMet()) : "")
                + r.quest().map(q -> "  quest " + q + met(r.questMet())).orElse("")
                + "  " + skills(r.skills(), r.skillsMet()));

        out.add("§7ids (" + d.identifications().size() + "):");
        for (ItemCardData.Identification id : d.identifications()) {
            String value = id.displayRange() != null
                    ? id.displayRange().low() + " to " + id.displayRange().high()
                    : (id.value() > 0 ? "+" : "") + id.value();
            out.add("  " + (id.positive() ? "§a" : "§c") + value + id.unit() + " §f" + id.displayName()
                    + "§7" + id.percentage().map(p -> "  " + fmt(p) + "%" + (id.estimated() ? "~" : "")).orElse("")
                    + (id.fixed() ? " fixed" : "") + (id.perfectInternalRoll() ? " ★" : ""));
        }

        d.majorId().ifPresent(m -> out.add("§d✦ " + (m.name().isEmpty() ? "Major ID" : m.name()) + "§7: " + m.plainText()));

        ItemCardData.Powders p = d.powders();
        if (p.slots() > 0 || !p.applied().isEmpty()) {
            out.add("§7powders [" + p.applied().size() + "/" + p.slots() + "]"
                    + (p.known() ? "" : " (flip to page 2 to read)") + " "
                    + p.applied().stream().map(e -> e.powder().name() + " " + e.tier()).collect(Collectors.joining(", "))
                    + p.specialName().map(s -> "  special=" + s).orElse(""));
        }

        d.shinyStat().ifPresent(s -> out.add("§b✧ " + s.displayName() + ": " + s.value() + s.unit()
                + " (" + s.shinyRerolls() + " shiny rerolls)"));

        if (!d.scales().isEmpty()) {
            out.add("§7scales: " + d.scales().stream()
                    .map(s -> s.source() + " " + s.name() + " Scale " + fmt(s.percentage()) + "%")
                    .collect(Collectors.joining(", ")));
        }

        d.crafter().ifPresent(c -> out.add("§7crafted by " + c));
        return out;
    }

    private static String skills(Map<Skill, Integer> skills, Map<Skill, Boolean> met) {
        StringBuilder sb = new StringBuilder();
        for (Skill skill : Skill.values()) {
            int v = skills.getOrDefault(skill, 0);
            if (v == 0) continue;
            sb.append(skill.name(), 0, 3).append(' ').append(v)
                    .append(met(met == null ? null : met.get(skill))).append(' ');
        }
        return sb.isEmpty() ? "no skill reqs" : sb.toString().strip();
    }

    private static String met(Boolean met) {
        if (met == null) return "";
        return met ? "§a✔§7" : "§c✖§7";
    }

    private static String fmt(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }
}
