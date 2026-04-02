package com.wynnweaponviewer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModCommands {

    private static final List<String> STATE_OPTIONS = List.of("on", "off", "toggle");

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("WynnWeaponBigger")
                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                        .suggests(ModCommands::suggestStates)
                        .executes(ModCommands::executeCommand)));

        dispatcher.register(ClientCommandManager.literal("WWB")
                .then(ClientCommandManager.argument("state", StringArgumentType.word())
                        .suggests(ModCommands::suggestStates)
                        .executes(ModCommands::executeCommand)));
    }

    private static CompletableFuture<Suggestions> suggestStates(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(STATE_OPTIONS, builder);
    }

    private static int executeCommand(CommandContext<FabricClientCommandSource> context) {
        String state = StringArgumentType.getString(context, "state");
        FabricClientCommandSource source = context.getSource();

        switch (state.toLowerCase()) {
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
