package com.wynnweaponviewer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

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
                    .then(ClientCommandManager.literal("toggle").executes(ctx -> setState(ctx, "toggle"))));
        }
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
