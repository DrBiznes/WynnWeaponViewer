# Wynn Weapon Bigger v1.0.0 - Major Rewrite

This document covers the major changes made to Wynn Weapon Bigger, transitioning from a simple matrix-based renderer to a modern PictureInPicture (PIP) GPU rendering system based on ItemZoomer.

## Overview

The mod has been completely rewritten to use Minecraft 1.21.11's new PictureInPicture rendering system, which provides:
- GPU-accelerated item rendering
- Smooth animations (appear and idle)
- Better visual quality
- Configurable animation styles

## Key Changes from Previous Version

### Rendering System

**Old System (v0.x):**
- Used simple matrix transforms with `DrawContext.drawItem()`
- Direct rendering during screen render event
- No animation support
- Basic scaling via `PoseStack` transforms

**New System (v1.0):**
- Uses Minecraft's `PictureInPictureRenderer` system
- Renders items to a GPU texture, then blits to screen
- Supports complex animations
- Better handling of large Wynncraft weapon models

### Architecture Changes

**Old Structure:**
```
com.wynnweaponviewer/
├── WynnWeaponViewer.java (main + rendering)
├── config/ModConfig.java
├── mixin/MixinHandledScreen.java (render injection)
└── HorizontalAlignment.java
```

**New Structure:**
```
com.wynnweaponviewer/
├── WynnWeaponViewer.java (main entry, event registration)
├── ModCommands.java (chat commands)
├── HorizontalAlignment.java
├── config/
│   └── ModConfig.java (expanded with animation settings)
├── accessor/
│   ├── ContainerScreenAccessor.java
│   └── RecipeBookScreenAccessor.java
├── mixin/
│   ├── AbstractContainerScreenMixin.java
│   ├── AbstractRecipeBookScreenMixin.java
│   ├── GuiRendererMixin.java (PIP registration)
│   ├── GuiGraphicsAccessor.java
│   ├── GuiRenderStateAccessor.java
│   └── PictureInPictureRendererAccessor.java
└── render/
    ├── AnimationState.java (animation timing)
    ├── ZoomedItemRenderer.java (main render logic)
    ├── ZoomedItemRenderState.java (render state record)
    └── ZoomedItemPIPRenderer.java (GPU texture rendering)
```

## How the New Renderer Works

### 1. PictureInPicture System

The PIP system works in three stages:

1. **State Submission**: `ZoomedItemRenderer` creates a `ZoomedItemRenderState` containing:
   - The item's render state
   - Display bounds (x0, y0, x1, y1)
   - Rotation angle (for idle animations)
   - Alpha value (for fade animations)
   - Optional scissor rectangle (for slide animations)

2. **Texture Rendering**: `ZoomedItemPIPRenderer.renderToTexture()` renders the item model to a GPU texture:
   ```java
   poseStack.scale(MODEL_SCALE, -MODEL_SCALE, -MODEL_SCALE);
   itemState.submit(poseStack, submitNodeCollector, ...);
   ```
   The `MODEL_SCALE` (0.50) scales down the model to fit larger Wynncraft weapons.

3. **Blitting**: `ZoomedItemPIPRenderer.blitTexture()` draws the texture to the screen with proper alpha blending.

### 2. Animation System

`AnimationState` tracks:
- Current hovered item
- Animation start time
- Hover duration

It provides:
- `getAppearProgress()` - Progress of appear animation (0.0 to 1.0)
- `getIdleAnimationTime()` - Time for idle animation calculations
- `getTextAppearProgress()` - Progress for item info text fade-in

### 3. Wynntils Integration

The filtering logic remains similar to the old version:

```java
private static boolean isValidWynncraftItem(ItemStack itemStack) {
    return Models.Item.asWynnItem(itemStack, GearItem.class)
            .map(gearItem -> {
                GearType gearType = gearItem.getGearType();
                if (gearType.isWeapon()) return config.enableWeapons;
                else if (gearType.isArmor()) return config.enableArmor;
                else if (gearType == GearType.RING || ...) return config.enableAccessories;
                return false;
            })
            .orElse(false) || (config.enableUnidentified && isUnidentifiedItem(itemStack));
}
```

Trade market sold items (Expired/Fulfilled) are still filtered out.

## New Configuration Options

### Animation Settings (New)

| Setting | Default | Description |
|---------|---------|-------------|
| `appearAnimation` | FADE | Animation when item preview appears (NONE, FADE, SCALE, SLIDE_LEFT, SLIDE_RIGHT, SLIDE_TOP, SLIDE_BOTTOM) |
| `idleAnimation` | NONE | Animation while hovering (NONE, SWING, PULSE) |
| `appearDelayMs` | 250 | Delay before animation starts (0-1000ms) |
| `showItemInfo` | true | Show item name/stats below preview |
| `infoDelaySeconds` | 2 | Delay before showing item info (0-10s) |

### Existing Settings (Preserved)

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | true | Enable/disable mod |
| `scale` | 0.4 | Zoom scale (0.1-1.0) - now also affects text size |
| `horizontalAlignment` | RIGHT | Display position (LEFT, RIGHT) |
| `xOffset` | 0 | Horizontal offset (-500 to 500) |
| `yOffset` | 0 | Vertical offset (-500 to 500) |
| `showToggleMessage` | true | Show chat message on toggle |
| `enableWeapons` | true | Show Wynncraft weapons |
| `enableArmor` | false | Show Wynncraft armor |
| `enableAccessories` | false | Show Wynncraft accessories |
| `enableUnidentified` | false | Show unidentified items |
| `enableCorkianAmplifiers` | false | Show Corkian Amplifiers |

## Technical Details

### Mixin Registration

The `GuiRendererMixin` registers our custom PIP renderer:

```java
@Inject(method = "<init>", at = @At("RETURN"))
private void wynnweapon$onInit(...) {
    wynnweapon$zoomedItemRenderer = new ZoomedItemPIPRenderer(this.bufferSource);
    pictureInPictureRenderers.put(ZoomedItemRenderState.class, wynnweapon$zoomedItemRenderer);
}
```

### Model Scale for Wynncraft Weapons

Wynncraft weapon models are larger than standard Minecraft items. The `MODEL_SCALE` constant in `ZoomedItemPIPRenderer` (set to 0.50) scales down models to fit within the render texture:

```java
private static final float MODEL_SCALE = 0.50F;

protected void renderToTexture(ZoomedItemRenderState state, PoseStack poseStack) {
    poseStack.scale(MODEL_SCALE, -MODEL_SCALE, -MODEL_SCALE);
    // ... render item
}
```

### Text Scaling

The item info text now scales with the zoom setting:

```java
float baseTextScale = zoomScale * 2.5f;
```

This ensures text remains proportional to the item preview size.

## Mappings Change

The project now uses **Mojang mappings** instead of Yarn mappings to better support the new 1.21.11 PIP rendering APIs:

```gradle
mappings loom.officialMojangMappings()
```

## Dependencies

- Minecraft 1.21.11
- Fabric Loader 0.18.1+
- Fabric API
- Cloth Config
- Wynntils

## Files Reference

| File | Purpose |
|------|---------|
| `ZoomedItemRenderer.java` | Main render logic, animation handling, Wynntils filtering |
| `ZoomedItemPIPRenderer.java` | GPU texture rendering, model scaling |
| `ZoomedItemRenderState.java` | Immutable state record for PIP system |
| `AnimationState.java` | Animation timing and progress tracking |
| `ModConfig.java` | Configuration with Cloth Config integration |
| `GuiRendererMixin.java` | Registers PIP renderer with Minecraft |
| `AbstractContainerScreenMixin.java` | Exposes screen properties via accessor |

## Credits

- Original Wynn Weapon Bigger by DrBiznes
- Rendering system based on [ItemZoomer](https://github.com/iMeeTake/ItemZoomer) by iMeeTake
- Requires [Wynntils](https://wynntils.com/) for item detection
