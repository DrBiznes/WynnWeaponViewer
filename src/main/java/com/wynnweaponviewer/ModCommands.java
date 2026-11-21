package com.wynnweaponviewer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.wynnweaponviewer.card.ItemCardCache;
import com.wynnweaponviewer.card.ItemCardData;
import com.wynnweaponviewer.card.ItemCardDataFactory;
import com.wynnweaponviewer.card.ItemCardDebug;
import com.wynnweaponviewer.render.ZoomedItemRenderer;
import com.wynnweaponviewer.render.card.CardFrameRenderer;
import com.wynnweaponviewer.render.card.WynnGlyphs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

public class ModCommands {

    /**
     * Brigadier literals are case-sensitive, and anything the client doesn't match is sent to the Wynncraft
     * server ("Invalid command..."), so register both the original and lowercase spellings.
     */
    private static final List<String> ROOTS = List.of("WynnWeaponBigger", "wynnweaponbigger", "WWB", "wwb");

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        for (String root : ROOTS) {
            dispatcher.register(ClientCommandManager.literal(root)
                    // Literals (not a free-form word argument) so they can't clash with "debug".
                    .then(ClientCommandManager.literal("on").executes(ctx -> setState(ctx, "on")))
                    .then(ClientCommandManager.literal("off").executes(ctx -> setState(ctx, "off")))
                    .then(ClientCommandManager.literal("toggle").executes(ctx -> setState(ctx, "toggle")))
                    .then(ClientCommandManager.literal("debug")
                            .then(ClientCommandManager.literal("cardpreview")
                                    .executes(ModCommands::toggleCardPreview))
                            .then(ClientCommandManager.literal("card")
                                    .executes(ctx -> debugCard(ctx, false))
                                    .then(ClientCommandManager.literal("hand")
                                            .executes(ctx -> debugCard(ctx, true))))
                            .then(frameCommand())));
        }
    }

    /** {@code debug frame <auto|perfect|defective|none>}: preview the special frames on any item. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> frameCommand() {
        var frame = ClientCommandManager.literal("frame")
                .executes(ctx -> forceFrame(ctx, null));
        frame.then(ClientCommandManager.literal("auto").executes(ctx -> forceFrame(ctx, null)));
        for (CardFrameRenderer.Effect effect : CardFrameRenderer.Effect.values()) {
            String name = effect.name().toLowerCase(java.util.Locale.ROOT);
            frame.then(ClientCommandManager.literal(name).executes(ctx -> forceFrame(ctx, effect)));
        }
        return frame;
    }

    private static int forceFrame(CommandContext<FabricClientCommandSource> context, CardFrameRenderer.Effect effect) {
        CardFrameRenderer.Effect.forced = effect;
        ZoomedItemRenderer.invalidateLayouts();
        context.getSource().sendFeedback(Component.literal("§6[Weapon Viewer] Card frame: §f"
                + (effect == null ? "auto (from the item's roll)" : effect.name().toLowerCase(java.util.Locale.ROOT))
                + (WynnGlyphs.isPackActive() ? "" : " §7(needs the Wynncraft pack)")));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleCardPreview(CommandContext<FabricClientCommandSource> context) {
        boolean enabled = !ZoomedItemRenderer.isCardPreview();
        ZoomedItemRenderer.setCardPreview(enabled);
        WynnWeaponViewer.LOGGER.info("Card preview {} (Wynncraft pack glyphs {})",
                enabled ? "enabled" : "disabled", WynnGlyphs.isPackActive() ? "found" : "missing");
        context.getSource().sendFeedback(Component.literal("§6[Weapon Viewer] Card preview "
                + (enabled ? "on" : "off") + "§7 (pack glyphs " + (WynnGlyphs.isPackActive() ? "found" : "missing - plain skin") + ")"));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Dumps the item card snapshot. Defaults to the last item hovered in an inventory (hover it, close the
     * inventory, run the command); falls back to - or with {@code hand}, forces - the main-hand item.
     */
    private static int debugCard(CommandContext<FabricClientCommandSource> context, boolean forceHand) {
        FabricClientCommandSource source = context.getSource();
        Optional<ItemCardData> data = Optional.empty();
        String origin = "last hovered item";

        if (!forceHand) data = ItemCardCache.getLastHovered();
        if (data.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // The main-hand stack is the live inventory stack, so it still carries the Wynntils annotation.
                data = ItemCardDataFactory.create(mc.player.getMainHandItem());
                origin = "main hand";
            }
        }

        if (data.isEmpty()) {
            source.sendError(Component.literal("§c[Weapon Viewer] No Wynncraft gear found. Hover an item in an inventory or hold one."));
            return 0;
        }

        source.sendFeedback(Component.literal("§6[Weapon Viewer] Card data (" + origin + "):"));
        for (String line : ItemCardDebug.describe(data.get())) {
            source.sendFeedback(Component.literal(line));
        }
        WynnWeaponViewer.LOGGER.info("Item card data ({}): {}", origin, data.get());
        return Command.SINGLE_SUCCESS;
    }

    private static int setState(CommandContext<FabricClientCommandSource> context, String state) {
        FabricClientCommandSource source = context.getSource();

        switch (state) {
            case "on":
                WynnWeaponViewer.setZoomEnabled(true);
                source.sendFeedback(Component.literal("\u00A76[Weapon Viewer] Enabled"));
                break;
            case "off":
                WynnWeaponViewer.setZoomEnabled(false);
                source.sendFeedback(Component.literal("\u00A76[Weapon Viewer] Disabled"));
                break;
            case "toggle":
                WynnWeaponViewer.toggleZoom();
                source.sendFeedback(Component.literal("\u00A76[Weapon Viewer] " + (WynnWeaponViewer.isZoomEnabled() ? "Enabled" : "Disabled")));
                break;
            default:
                source.sendError(Component.literal("\u00A7cInvalid argument. Use 'on', 'off', or 'toggle'."));
                return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}
