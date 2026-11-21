# Wynn Weapon Bigger - Changelog

## v1.0.0

### Highlights

This is a major rewrite of the mod. The rendering system has been completely overhauled to use Minecraft 1.21.11's PictureInPicture GPU rendering, resulting in much better visual quality and smoother performance.

---

### New Features

**Appear Animations**
Choose how the item preview appears when you hover over an item:
- `NONE` - instant
- `FADE` - fades in (default)
- `SCALE` - scales up from nothing
- `SLIDE_LEFT`, `SLIDE_RIGHT`, `SLIDE_TOP`, `SLIDE_BOTTOM` - slides in from a direction

**Idle Animations**
Optional animations while hovering:
- `SWING` - gentle rocking
- `PULSE` - subtle breathing scale

**Item Info Text**
The item name and stats now appear below the preview after a configurable delay. Text size scales proportionally with the zoom level. The item name can optionally be shown above the preview instead of below.

**Appear Delay**
Configurable delay (0–1000ms) before the item preview appears, so it doesn't flash on every quick mouse movement.

---

### Configuration Changes

All settings are available through Mod Menu.

| Setting | Default | Description |
|---------|---------|-------------|
| `appearAnimation` | FADE | Animation style when preview appears |
| `idleAnimation` | NONE | Animation while hovering |
| `appearDelayMs` | 250 | Delay before preview appears (ms) |
| `showItemInfo` | true | Show item name and stats |
| `infoDelaySeconds` | 0 | Delay before item info appears (seconds) |
| `itemNameAbove` | false | Show item name above preview instead of below |

All existing settings (`scale`, `horizontalAlignment`, `xOffset`, `yOffset`, `enableWeapons`, etc.) are preserved from v0.x.

---

### Compatibility

- Minecraft 1.21.11
- Requires Wynntils
- Requires Fabric API, Cloth Config

---

### Credits & Attributions

- **Big Items, Duh** — the original mod this project was forked from
- **ItemZoomer** by iMeeTake — the rendering system the v1.0 rewrite is based on
- **Wynntils** — item detection and lore utilities that make this mod possible
- Special thanks to **Igbarvonsquid**!!!!!
