# Item Card Revamp — Plan

Replace `ZoomedItemRenderer.renderItemInfo()` (which dumps vanilla tooltip lines in `mc.font` at fractional scale)
with a **Wynncraft-styled "showcase card"** built from Wynntils' structured data and rendered with the
Wynncraft server pack's own fonts and sprites. The card and the big model are laid out together as one composition.

---

## 1. What we found

### Our mod today
- `renderItemInfo()` calls `stack.getTooltipLines()`. That returns **raw Wynncraft lore**, not Wynntils' tooltip.
  The lore is full of glyph fonts and `minecraft:space` offset codepoints.
- We then re-wrap those lines with `font.split()`, which breaks the alignment glyphs. We also tint every line
  white/grey, which throws away the tier colours.
- Text scale is `config.scale * 2.5`. That is fractional, so the pixel fonts shimmer and blur.
- The text is drawn *over* the bottom of the model (`textY = itemY + itemSize*0.93 - 8`). The layout doesn't reserve space for it.

### Wynncraft resource pack (server pack, v2.2 / 26.3, cached in the Modrinth profile `downloads/`)
- The pack zip is obfuscated: local headers don't match, and PNG CRC/adler checksums are zeroed. MC doesn't care.
  We never ship or copy its assets. We only reference them by id at runtime, and they're present whenever the player is on Wynncraft.
- The tooltip is made of two kinds of asset:
  - **Per-tier 9-slice sprites:** `gui/sprites/tooltip/{normal,unique,rare,legendary,fabled,mythic,crafted,…}[_shiny]_{frame,background}.png`.
    They are 146×146 with border 14/20, `stretch_inner`. The stack's `minecraft:tooltip_style` component points at these.
  - **Glyph fonts:** the lore text itself, drawn in these fonts (all `minecraft:` namespace):
    - `tooltip/banner`: element/untradable icons
    - `tooltip/requirement/{sprite,frame}`: STR/DEX/INT/DEF/AGI icons and the tier-coloured diamond frames
    - `tooltip/divider`: the ◇ line
    - `tooltip/emblem/{frame,sprite}`: the big item-type emblem, framed per tier
    - `tooltip/attribute/sprite`: element damage icons and the attack-speed icon
    - `tooltip/identification/{meter,major}`: roll meter and major ID icon
    - `tooltip/socket`: powder sockets
    - `banner/box` and `banner/pill`: MYTHIC/SPEAR text badges
    - `offset/wynncraft_quad/12`: the big "604" DPS number
    - `language/wynncraft`: the pixel text font
    - `space`: offsets

### Wynntils 4.2.17 (every API below verified against our actual dependency jar)
- **Data:** `Models.Item.asWynnItem(stack, GearItem.class)` gives you:
  - `GearInfo` (static API data): `tier`, `type`, `elements`, `fixedStats` (averageDps, attackSpeed, damages, defences, majorIds), `requirements`, `powderSlots`
  - The rolled instance: `getIdentifications()`, `getOverallPercentage()`, `isPerfect()`/`isDefective()`, `getShinyStat()`, `getPowders()`, `getRerollCount()`, `getGearInstanceRequirements()`
- **Roll %:**
  - `StatCalculator.calculatePercentage(actual, possible)` returns the % plus an `estimated` flag.
  - `ItemStatInfoFeature.LERP_MAP` is the colour scale (0 red → 40 gold → 70 yellow → 90 green → 100 aqua).
  - `ColorScaleUtils.getPercentageTextComponent(...)` formats it.
- **Scales:** `Services.ItemWeight.getItemWeighting(name, NORI|WYNNPOOL)` + `calculateWeighting(w, gearItem)` give the "Main Scale" / "Raid Scale" lines.
- **Glyph builders:**
  - `BannerBoxFont.buildMessage(text, bg, fg, suffix)` builds text badges.
  - `TooltipBannerFont.buildMessage(icon, bg, suffix)` builds icon badges.
  - Font ids live in `CommonFonts`.
  - Glyph codepoints live in `Element`, `Skill`, `DamageType`, `GearType`, and `RequirementsLineBuilder`.
  - `FontManager.calculateOffset()` produces space glyphs.
- **Line builders:** `HeaderStatsLineBuilder`, `RequirementsLineBuilder`, `IdentificationLineBuilder` and `PowderLineBuilder` return ready `Component`s.
  `TooltipUtils.getWynnItemTooltip(stack, item)` returns Wynntils' whole tooltip, pre-aligned.
- **Pitfalls:**
  - `ItemStack.copy()` loses the Wynntils annotation. Always query the live slot stack. Check the carried/cursor stack too.
  - Powders and set info only exist once page 1 of the tooltip has been viewed (`hasParsedPage(1)`).
  - `fixedStats()` damages exclude powders. Use `WynnItemParser.parseItemStackStatsPage(stack, info.getVariableStatsMap())` on page 0 for the values with powders included.
  - Unidentified items have no instance. Crafted and unknown gear have no `GearInfo`. Ascended mythics swap `GearInfo` at runtime, so re-read it every time.
  - Pre-aligned Wynntils/Wynncraft lines must never be re-wrapped, and can only be scaled uniformly.

---

## 2. Design: the Showcase Card

The vanilla tooltip already shows everything at 1×. The card's job is to be the **beautiful, glanceable, screenshot-worthy**
version that sits under the giant model. It uses the same visual language, the same frame, and is curated.

```
            ┌─[ mythic_frame / mythic_background, 9-slice ]──────────┐
            │  ◆emblem◆  Idol                         [73.7%]        │  ← name in language/wynncraft, tier colour; overall % in LERP colour
            │            [MYTHIC] [SPEAR] [◉ WATER]                  │  ← banner/box + tooltip/banner badges
            │ ─────────────────────── ◇ ──────────────────────────── │  ← tooltip/divider, tinted by tier
            │   604  DPS          ⚔ Normal · 2.05 hits/s             │  ← offset/wynncraft_quad/12 hero number
            │   ◉ 250–339                                            │  ← attribute sprites, per-element colour
            │ ─────────────────────── ◇ ──────────────────────────── │
            │  [STR][DEX][INT][DEF][AGI]   Warrior · Lv 95  ✔        │  ← requirement sprite + frame row, met/unmet
            │     0    0   120   0    0                              │
            │ ─────────────────────── ◇ ──────────────────────────── │
            │  Spell Damage       +319  ▰▰▰▰▰▰▰▰▱ 90.9%              │  ← top-N IDs sorted by Wynntils ordering,
            │  Reflection          +37% ▰▰▰▰▰▰▰▰▱ 93.3%              │    meter glyph + LERP colour %
            │  Charge Cost          -44 ▰▰▰▰▰▱▱▱▱ 58.0%              │
            │  ✦ Tidal Drift — Flying Kick will instead send …       │  ← major ID (wrapped, its own block)
            │  ◌◌◌ powders     Nori Main 69.7% · Wynnpool Raid 83.4% │  ← sockets + scales (optional)
            └────────────────────────────────────────────────────────┘
```

### Principles
1. **Build from data, not from lore.** Every element is our own `Component`/sprite call fed by Wynntils models,
   so we control layout, wrapping and priority. The one exception is "Mirror" mode (below).
2. **Pixel-perfect scaling.** All card content is laid out in 1× "tooltip pixels". It is then drawn under one
   `pose().scale(s)`, where `s` snaps to a multiple of `1/guiScale`. That way each glyph texel maps to whole
   screen pixels. No more fractional `2.5×` blur.
3. **The model and the card share one layout.** Measure the card first. Then size the model in the space that's left,
   and centre the pair as one column. The card no longer overlaps the model.
4. **Degrade by priority, not by shrinking.** These always show: Header, Badges, Hero stat.
   The other sections drop out in this order as height runs short: Scales → Powders → Major ID → IDs (top-N shrinks first) → Requirements → Damage row.
5. **Graceful fallback.** If the Wynncraft pack isn't active (check `ResourceManager` for `minecraft:font/tooltip/banner.json`), the card
   switches to a plain-text skin (`mc.font` + `ChatFormatting` tier colours) with a vanilla tooltip background.

### Variants by item kind
| Kind | Hero block | Body |
|---|---|---|
| Weapon (`GearItem`, `isWeapon`) | DPS + attack speed + damage per element | reqs, IDs, major ID, powders |
| Armor / accessory | Health + defence per element | reqs, IDs, major ID, powders |
| Unidentified (`GearBoxItem` or `isUnidentified`) | tier + type + level range | "Unidentified" badge, ID *ranges* via `StatCalculator.getDisplayRange` |
| Crafted (`CraftedGearItem`) | own DPS/damages getters | crafted tier colours, durability, IDs (meter-only %) |
| Unknown (`UnknownGearItem`) | name + badges only | — |

### Info styles (config `infoStyle`)
- `CARD` (default): the showcase card described above.
- `COMPACT`: header + badges + hero stat only. A slim nameplate under the model.
- `MIRROR`: renders `TooltipUtils.getWynnItemTooltip(stack, item)` exactly, inside the tier frame, uniformly scaled.
  It's a near-free fallback and handy for comparing against the real tooltip.
- `OFF`

---

## 3. Architecture

```
render/
  ZoomedItemRenderer        → becomes the orchestrator: pick stack → build/get card → layout composition → submit model PIP + draw card
  composition/
    PreviewLayout            single pass: available rect → (modelRect, cardRect) for LEFT/RIGHT alignment, offsets, nameAbove legacy
  card/
    ItemCardData             immutable snapshot: tier, type, name, overall%, badges, hero stats, damages, reqs, IDs, majorId, powders, scales, tooltipStyle id
    ItemCardDataFactory      Wynntils → ItemCardData (GearItem / Crafted / Unknown / GearBox branches; powdered damages via WynnItemParser when on page 0)
    ItemCardCache            keyed by (Slot stack identity, Wynntils page, overall%, powder count); rebuilt only on change
    CardSkin                 interface: WynncraftSkin (glyph fonts + tooltip sprites) | PlainSkin (fallback)
    blocks/                  CardBlock { measure(width) ; render(g, x, y, alpha) ; priority }
      HeaderBlock, BadgeRowBlock, DividerBlock, HeroStatBlock, DamageRowBlock,
      RequirementRowBlock, IdentificationListBlock, MajorIdBlock, PowderScaleRowBlock
    CardLayout               fit blocks into maxHeight by priority, compute integer-snapped scale
    CardFrameRenderer        blitSprite(tooltip/<style>_background|_frame) 9-slice at card size; vanilla TooltipRenderUtil as fallback
  glyph/
    WynnGlyphs               one place for font ids (CommonFonts re-exports) + codepoints we use; `isPackActive()`
```

Key APIs to wire up. Signatures are verified in the Wynntils 4.2.17 jar. The vanilla 1.21.11 ones need confirming at implementation time.
- Frame: `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("tooltip/" + style + "_frame"), x, y, w, h)`,
  using `style = stack.get(DataComponents.TOOLTIP_STYLE)`, or `GearTier.getTooltipStyle(shiny)` if that's missing.
- Text: `Component.literal(..).withStyle(s -> s.withFont(CommonFonts.WYNNTILS_LANGUAGE_WYNNCRAFT_FONT).withColor(tier.getChatFormatting()))`.
- Badges: `BannerBoxFont.buildMessage(tier.getApiName(), CustomColor.fromChatFormatting(tier.getChatFormatting()), CommonColors.BLACK, "")`.
  Only use `BannerBoxFont`. `BannerPill/Flag/Ribbon` are newer than 4.2.17.
- Requirements: `RequirementsLineBuilder.buildRequirements(tier, info.requirements(), gear.getGearInstanceRequirements())`,
  or compose our own from the same glyphs for a bigger row.
- IDs: `StatCalculator.calculatePercentage`, `Models.Stat.getOrderingList`, and `ColorScaleUtils.getWheelTextComponent` for the meter.
  Respect the user's Wynntils colour map via `Handlers.Tooltip.getTooltipOptions().colorMap()`.
- Scales: `Services.ItemWeight` with `ItemWeightSource.NORI`/`WYNNPOOL`, showing the top-1 or top-2 weightings.

---

## 4. Revised direction (2026-11-20): the card is a showcase, not a tooltip replacement

Wynncraft's tooltip stays open next to the card with every detail, so the card drops DPS/damage, attack speed,
skill-point costs, class/level and major ID text. It keeps: header (emblem, name + overall %, rarity/type/element badges,
**major ID name badge**), the 3D model *inside* the card, IDs with roll %, scales, powders, shiny stat, rerolls.
Fixed card width (`CardLayout.CONTENT_WIDTH`) so on-screen and shared cards look identical.

1. ✅ **Card frames the model** — `CardLayout` (fixed-width geometry), `ItemCardRenderer` (background → model → text),
   `ZoomedItemRenderer.renderCardComposition` (card fitted beside the inventory, appear/idle animations). Draw order relies
   on `GuiRenderState` stacking overlapping submissions on higher layers.
2. ✅ **Card body** — `CardBody` + `CardRow`: ID rows (name · value · roll %, 1px roll bar in the user's Wynntils colour
   scale; ranges for unidentified), footer (powder sockets + rerolls, shiny stat, top Nori/Wynnpool scale each).
   Sizing (revised after in-game test): one scale per screen from `CardLayout.REFERENCE_OUTER_HEIGHT` so every card
   is the same size; per item the model window flexes between 0.55:1 and 1.25:1 to absorb row-count differences;
   only if that isn't enough does `CardBody.Density` FULL → CAPPED (10 IDs) → MINIMAL (6 IDs, no footer) trim rows.
   ID rows are 15px with the roll bar 2px under the text.
3. ✅ **Motion & polish**
   - ✅ Shiny: Wynncraft's own `_shiny` sprites, animated by the pack.
   - ✅ Perfect (100%): rainbow flowing around the border; defective (0%): grey static with a failing-light flicker
     on a drained grey background. Both tint a white mask of the item's own frame sprite (`FrameMasks`, built from the
     sprite PNG + 9-slice mcmeta, rebuilt on resource reload) strip by strip (`CardFrameRenderer.Effect`), so the
     effect keeps the tier frame's shape.
     Preview on any item with `/wwb debug frame perfect|defective|none|auto`.
   - ✅ Idle animation is off inside the card unless `cardIdleAnimation` is enabled.
   - ✅ Staggered fade: text lines cascade in top to bottom (90ms fade + 3px rise each, 12-20ms apart, capped so
     the card is settled ~0.25s after it appears). Optional via `cardStaggeredFade` (default on).
4. **Make it the default** — config (sections, size), retire the floating model + old text info.
5. **Share** — keybind to save/copy the card as PNG (offscreen render); later chat-share via Wynntils item encoding.

## 5. Original phases (superseded by section 4 from phase 3 on)

1. ✅ **Data layer** (done — lives in `com.wynnweaponviewer.card`: `ItemCardData`, `ItemCardDataFactory`, `ItemCardCache`, `ItemCardDebug`;
   `/wwb debug card` dumps the last hovered item, `/wwb debug card hand` the main-hand item; full record also logged). Build `ItemCardData`, the factory and the cache. Add `/wwb debug card` to dump the snapshot to chat/log,
   so the data can be checked on real items (mythic, unid box, crafted, shiny, ascended) before any rendering exists.
   Fix stack handling: no `copy()` for lookups; check the cursor stack annotation.
2. ✅ **Glyph + frame layer** (done — `render/card`: `WynnGlyphs`, `CardFrameRenderer`, `CardSkin` → `WynncraftSkin`/`PlainSkin`,
   `ItemCardRenderer`; header + badges only, opt-in via `/wwb debug cardpreview`). Original scope: `WynnGlyphs`, pack detection, `CardFrameRenderer`, and both skins. Render a static test card with header + badges.
3. **Block layout engine.** `CardBlock` measure/render, priority fitting, integer-snapped scale, divider tinting via `TooltipBuilder.getDividerColor`.
4. **Composition with the model.** `PreviewLayout` replaces the inline maths in `renderZoomedItem`. Size the model from the remaining height.
   Support LEFT/RIGHT alignment, offsets, and a small-screen fallback to `COMPACT`.
5. **Motion & polish.**
   - Stagger the block fade-in using the existing `AnimationState` text progress. Header first, ~40 ms per block.
   - Card slides with the model for `SLIDE_*` modes.
   - Shiny: use the `_shiny` style, which the pack animates via `.mcmeta`.
   - Perfect rolls: rainbow (`hasFullPerfectInternalRolls`).
6. **Config + i18n.**
   - New: `infoStyle`, `cardScale` (separate from model `scale`), per-section toggles (reqs/IDs/majorId/powders/scales), `maxIdentifications`, `showFrame`.
   - Migrate the old `showItemInfo`/`itemNameAbove`/`infoDelaySeconds` (keep the delay). Update `en_us.json` and the Cloth screen.
7. **Test matrix in-game.**
   - Every weapon type, armor and accessory, every tier and shiny.
   - Unidentified box, crafted, ascended mythic.
   - Wynntils ItemStatInfo on and off.
   - GUI scale 2/3/4/auto, small windows.
   - Pack declined (fallback skin), carried item, trade market.

## 5. Open decisions
- Card shows a curated top-N of IDs (recommended, default 5) vs. all IDs. The vanilla tooltip already lists all of them.
- Whether `MIRROR` mode ships in v1.1 or stays a debug option.
- Card placement: under the model (default) vs. beside it on ultrawide layouts. This could be automatic when width > ~2× height.
