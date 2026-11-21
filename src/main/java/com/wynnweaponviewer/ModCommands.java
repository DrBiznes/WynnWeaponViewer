package com.wynnweaponviewer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.wynnweaponviewer.config.ConfigScreens;
import com.wynnweaponviewer.config.ModConfig;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.nio.file.Path;
import java.util.List;

public class ModCommands {

    /**
     * Brigadier literals are case-sensitive, and anything the client doesn't match is sent to the Wynncraft
     * server ("Invalid command..."), so register both the original and lowercase spellings.
     */
    private static final List<String> ROOTS = List.of("WynnWeaponBigger", "wynnweaponbigger", "WWB", "wwb");

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        for (String root : ROOTS) {
            dispatcher.register(ClientCommandManager.literal(root)
                    .then(ClientCommandManager.literal("on").executes(ctx -> setState(ctx, "on")))
                    .then(ClientCommandManager.literal("off").executes(ctx -> setState(ctx, "off")))
                    .then(ClientCommandManager.literal("toggle").executes(ctx -> setState(ctx, "toggle")))
                    .then(ClientCommandManager.literal("config").executes(ModCommands::openConfig))
                    .then(ClientCommandManager.literal("reload").executes(ModCommands::reloadConfig)));
        }
    }

    /** Opens the settings screen (or, without Cloth Config, the screen pointing at the config file). */
    private static int openConfig(CommandContext<FabricClientCommandSource> context) {
        WynnWeaponViewer.openScreenNextTick(ConfigScreens.create(null));
        return Command.SINGLE_SUCCESS;
    }

    /** Re-reads the config file after it's been edited by hand. */
    private static int reloadConfig(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        if (ModConfig.reload()) {
            source.sendFeedback(Component.literal("§6[Weapon Viewer] Settings reloaded from ")
                    .append(fileLink()));
            return Command.SINGLE_SUCCESS;
        }
        source.sendError(Component.literal("§c[Weapon Viewer] Couldn't read the config file, so the settings "
                + "were reset. The old file was kept as " + ModConfig.file().getFileName() + ".broken"));
        return 0;
    }

    /** The config file's name, clickable to open it. */
    private static Component fileLink() {
        Path file = ModConfig.file();
        return Component.literal(file.getFileName().toString()).withStyle(style -> style
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenFile(file))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(file.toAbsolutePath().toString()))));
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
