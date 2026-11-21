package com.wynnweaponviewer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.wynnweaponviewer.HorizontalAlignment;
import com.wynnweaponviewer.WynnWeaponViewer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The mod's settings and their JSON file ({@code config/wynn_weapon_viewer.json}).
 *
 * <p>Plain data only - no Cloth Config or Mod Menu classes - so the mod runs, and the file can be edited by hand,
 * without either installed. The in-game screen lives in {@link ClothConfigScreen} and is only used when Cloth Config
 * is present.
 *
 * <p>Loading is forgiving: each field is read on its own, so a typo in one value only resets that value. Files from
 * v1.0.0 load as they are; options they don't have yet get their defaults, and the file is rewritten with them.
 */
public final class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("wynn_weapon_viewer.json");

    /** {@link Config#cardMaxIdentifications} at or above this shows every identification. */
    public static final int ALL_IDENTIFICATIONS = 21;

    private static Config config = new Config();
    /** Bumped on every change, so caches built from the settings (card layouts) know to rebuild. */
    private static int revision;

    static {
        load();
    }

    private ModConfig() {}

    public enum DisplayMode {
        /** The model inside a Wynncraft-style item card with its rolls. */
        CARD,
        /** The model on its own, with the item's name and badges floating under it. */
        FLOATING
    }

    public enum AppearAnimation {
        NONE,
        FADE,
        SCALE,
        SLIDE_LEFT,
        SLIDE_RIGHT,
        SLIDE_TOP,
        SLIDE_BOTTOM
    }

    public enum IdleAnimation {
        NONE,
        SWING,
        PULSE
    }

    public enum ShareAction {
        COPY_AND_SAVE,
        COPY_ONLY,
        SAVE_ONLY;

        public boolean copies() {
            return this != SAVE_ONLY;
        }

        public boolean keepsFile() {
            return this != COPY_ONLY;
        }
    }

    /**
     * Field names are the JSON keys. The v1.0.0 names are kept as they were so old files carry over.
     * Ranges are enforced by {@link #sanitize}.
     */
    public static class Config {
        // General
        public boolean enabled = true;
        public boolean showToggleMessage = true;

        // Display
        public DisplayMode displayMode = DisplayMode.CARD;
        /** 0.1-1. Floating: share of the free space the model fills. Card: 1.0 fills the free space, 0.4 gives 70%. */
        public float scale = 0.4F;
        public HorizontalAlignment horizontalAlignment = HorizontalAlignment.RIGHT;
        public int xOffset = 0;
        public int yOffset = 0;

        // Item card contents (also used for shared cards)
        /** 0 hides them, {@link #ALL_IDENTIFICATIONS} shows all. */
        public int cardMaxIdentifications = ALL_IDENTIFICATIONS;
        public boolean cardShowPowders = true;
        public boolean cardShowShinyStat = true;
        public boolean cardShowScales = true;
        /** Rainbow frame for perfect rolls, flickering grey for defective ones. */
        public boolean cardFrameEffects = true;

        // Floating item (displayMode FLOATING, or items without a card)
        public boolean showItemInfo = true;
        public boolean itemNameAbove = false;

        // Animations
        public int appearDelayMs = 250;
        public AppearAnimation appearAnimation = AppearAnimation.FADE;
        public int infoDelaySeconds = 0;
        /** Text cascades in line by line instead of just appearing. */
        public boolean textCascade = false;
        public IdleAnimation idleAnimation = IdleAnimation.NONE;
        /** Whether the idle animation also plays on the model inside the item card. */
        public boolean cardIdleAnimation = false;

        // Sharing (Ctrl+C)
        public ShareAction shareAction = ShareAction.COPY_AND_SAVE;
        /** Flip through the item's tooltip pages first, so powders are on the shared card. */
        public boolean shareReadAllPages = true;

        // Which items get the preview
        public boolean enableWeapons = true;
        public boolean enableArmor = false;
        public boolean enableAccessories = false;
        public boolean enableUnidentified = false;
        public boolean enableCorkianAmplifiers = false;
    }

    public static Config getConfig() { return config; }
    public static Config defaults() { return new Config(); }
    public static Path file() { return CONFIG_FILE; }
    public static int revision() { return revision; }

    public static boolean isEnabled() { return config.enabled; }
    public static boolean shouldShowToggleMessage() { return config.showToggleMessage; }
    public static boolean isCardMode() { return config.displayMode == DisplayMode.CARD; }

    public static void setEnabled(boolean enabled) {
        config.enabled = enabled;
        save();
    }

    /** Writes the current settings to disk (values out of range are corrected first). */
    public static void save() {
        sanitize(config);
        revision++;
        write(GSON.toJsonTree(config));
    }

    /**
     * Re-reads the file, e.g. after editing it by hand.
     *
     * @return false if the file couldn't be read or parsed (the broken file is kept as {@code .broken})
     */
    public static boolean reload() {
        boolean ok = load();
        revision++;
        return ok;
    }

    private static boolean load() {
        if (!Files.exists(CONFIG_FILE)) {
            config = new Config();
            write(GSON.toJsonTree(config));
            return true;
        }

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new JsonParseException("Expected a JSON object");
            json = root.getAsJsonObject();
        } catch (IOException | JsonParseException e) {
            WynnWeaponViewer.LOGGER.error("Couldn't read {}; using defaults", CONFIG_FILE, e);
            keepBrokenFile();
            config = new Config();
            write(GSON.toJsonTree(config));
            return false;
        }

        Config loaded = new Config();
        for (Field field : Config.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            JsonElement value = json.get(field.getName());
            if (value == null || value.isJsonNull()) continue; // new option (or removed by hand): keep the default
            try {
                Object parsed = GSON.fromJson(value, field.getGenericType());
                if (parsed != null) field.set(loaded, parsed); // unknown enum names parse as null
                else WynnWeaponViewer.LOGGER.warn("Config: unknown value {} for {}, using the default", value, field.getName());
            } catch (RuntimeException | IllegalAccessException e) {
                WynnWeaponViewer.LOGGER.warn("Config: invalid value {} for {}, using the default", value, field.getName());
            }
        }
        sanitize(loaded);
        config = loaded;

        // Rewrite the file if it's missing options, has unknown keys, or had values corrected.
        // Compared as text: JSON trees treat a parsed 0.4 and the float 0.4F as different numbers.
        JsonElement normalized = GSON.toJsonTree(config);
        if (!GSON.toJson(normalized).equals(GSON.toJson(json))) write(normalized);
        return true;
    }

    /** Clamps numbers into their ranges. */
    private static void sanitize(Config c) {
        c.scale = Float.isFinite(c.scale) ? Math.clamp(c.scale, 0.1F, 1.0F) : new Config().scale;
        c.xOffset = Math.clamp(c.xOffset, -500, 500);
        c.yOffset = Math.clamp(c.yOffset, -500, 500);
        c.cardMaxIdentifications = Math.clamp(c.cardMaxIdentifications, 0, ALL_IDENTIFICATIONS);
        c.appearDelayMs = Math.clamp(c.appearDelayMs, 0, 1000);
        c.infoDelaySeconds = Math.clamp(c.infoDelaySeconds, 0, 10);
    }

    /** Writes through a temporary file, so a crash mid-write can't leave a half-written config behind. */
    private static void write(JsonElement json) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Path temp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            WynnWeaponViewer.LOGGER.error("Couldn't save {}", CONFIG_FILE, e);
        }
    }

    private static void keepBrokenFile() {
        try {
            Files.move(CONFIG_FILE, CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            WynnWeaponViewer.LOGGER.warn("Couldn't keep a copy of the broken config", e);
        }
    }
}
