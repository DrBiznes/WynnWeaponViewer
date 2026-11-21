package com.wynnweaponviewer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wynnweaponviewer.config.ConfigScreens;
import com.wynnweaponviewer.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /wwb} - one root, three subcommands:
 * <ul>
 *   <li>{@code /wwb} - version, whether the preview is on, and the commands below (click one to type it)</li>
 *   <li>{@code /wwb toggle} - turn the preview on or off</li>
 *   <li>{@code /wwb config} - open the settings</li>
 *   <li>{@code /wwb reload} - re-read the config file after editing it by hand</li>
 * </ul>
 */
public final class ModCommands {

    public static final String ROOT = "wwb";

    private record Sub(String name, String description, Command<FabricClientCommandSource> action) {}

    private static final List<Sub> SUBCOMMANDS = List.of(
            new Sub("toggle", "Turn the item preview on or off", ModCommands::toggle),
            new Sub("config", "Open the settings", ModCommands::openConfig),
            new Sub("reload", "Re-read the config file after editing it", ModCommands::reload));

    private ModCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = literal(ROOT).executes(ModCommands::help);
        for (Sub sub : SUBCOMMANDS) {
            root.then(literal(sub.name()).executes(sub.action()));
        }
        dispatcher.register(root);
    }

    // --- chat formatting -------------------------------------------------------------------------------------

    /** "[WWB] " in gold, before every message the mod sends. */
    public static MutableComponent prefixed(Component message) {
        return Component.empty()
                .append(Component.literal("[WWB] ").withStyle(ChatFormatting.GOLD))
                .append(message);
    }

    public static MutableComponent prefixed(String message) {
        return prefixed(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    // --- subcommands -----------------------------------------------------------------------------------------

    private static int help(CommandContext<FabricClientCommandSource> context) {
        String version = FabricLoader.getInstance().getModContainer(WynnWeaponViewer.MOD_ID)
                .map(mod -> " v" + mod.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        boolean on = WynnWeaponViewer.isZoomEnabled();

        MutableComponent message = prefixed(Component.empty()
                .append(Component.literal("Wynn Weapon Bigger" + version).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - preview ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(on ? "on" : "off")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)));
        for (Sub sub : SUBCOMMANDS) {
            String command = "/" + ROOT + " " + sub.name();
            message.append("\n").append(Component.literal("  " + command)
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent.SuggestCommand(command))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to type it")))))
                    .append(Component.literal("  " + sub.description()).withStyle(ChatFormatting.GRAY));
        }
        context.getSource().sendFeedback(message);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> context) {
        WynnWeaponViewer.setZoomEnabled(!WynnWeaponViewer.isZoomEnabled());
        boolean on = WynnWeaponViewer.isZoomEnabled();
        context.getSource().sendFeedback(prefixed(Component.empty()
                .append(Component.literal("Item preview ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(on ? "on" : "off")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return Command.SINGLE_SUCCESS;
    }

    /** Opens the settings screen (or, without Cloth Config, the screen pointing at the config file). */
    private static int openConfig(CommandContext<FabricClientCommandSource> context) {
        WynnWeaponViewer.openScreenNextTick(ConfigScreens.create(null));
        return Command.SINGLE_SUCCESS;
    }

    /** Re-reads the config file after it's been edited by hand. */
    private static int reload(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        if (ModConfig.reload()) {
            source.sendFeedback(prefixed(Component.empty()
                    .append(Component.literal("Settings reloaded from ").withStyle(ChatFormatting.GRAY))
                    .append(fileLink())));
            return Command.SINGLE_SUCCESS;
        }
        source.sendError(prefixed(Component.literal("Couldn't read the config file, so the settings were reset. "
                + "The old file was kept as " + ModConfig.file().getFileName() + ".broken")
                .withStyle(ChatFormatting.RED)));
        return 0;
    }

    /** The config file's name, clickable to open it. */
    private static Component fileLink() {
        Path file = ModConfig.file();
        return Component.literal(file.getFileName().toString()).withStyle(style -> style
                .withColor(ChatFormatting.WHITE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenFile(file))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(file.toAbsolutePath().toString()))));
    }
}
