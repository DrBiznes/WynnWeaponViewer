package com.wynnweaponviewer.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shown instead of the settings editor when Cloth Config isn't installed: says where the config file is, with
 * buttons to open it and to reload it after editing.
 */
public class NoClothConfigScreen extends Screen {

    private static final String KEY = "config.wynn_weapon_viewer.no_cloth.";
    private static final int BUTTON_WIDTH = 200;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;

    @Nullable
    private final Screen parent;
    private List<FormattedCharSequence> message = List.of();
    private List<FormattedCharSequence> pathLines = List.of();
    private Component status = Component.empty();

    public NoClothConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("config.wynn_weapon_viewer.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int textWidth = Math.min(width - 40, 320);
        message = font.split(Component.translatable(KEY + "message"), textWidth);
        pathLines = font.split(Component.literal(ModConfig.file().toAbsolutePath().toString()), textWidth);

        int x = (width - BUTTON_WIDTH) / 2;
        int y = Math.max(textBottom() + 16, height / 2);
        addRenderableWidget(Button.builder(Component.translatable(KEY + "open_file"),
                button -> Util.getPlatform().openPath(ModConfig.file())).bounds(x, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "open_folder"),
                button -> Util.getPlatform().openPath(ModConfig.file().getParent())).bounds(x, y + 24, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "reload"), button -> {
            boolean ok = ModConfig.reload();
            status = Component.translatable(KEY + (ok ? "reloaded" : "reload_failed"));
        }).bounds(x, y + 48, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, y + 80, BUTTON_WIDTH, 20).build());
    }

    private int textTop() {
        return 40;
    }

    private int textBottom() {
        return textTop() + (message.size() + 1 + pathLines.size()) * 10;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int center = width / 2;
        graphics.drawCenteredString(font, title, center, 16, TEXT_COLOR);

        int y = textTop();
        for (FormattedCharSequence line : message) {
            graphics.drawCenteredString(font, line, center, y, TEXT_COLOR);
            y += 10;
        }
        y += 10;
        for (FormattedCharSequence line : pathLines) {
            graphics.drawCenteredString(font, line, center, y, MUTED_COLOR);
            y += 10;
        }

        int buttonsBottom = Math.max(textBottom() + 16, height / 2) + 100;
        graphics.drawCenteredString(font, status, center, Math.min(buttonsBottom + 6, height - 14), TEXT_COLOR);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
