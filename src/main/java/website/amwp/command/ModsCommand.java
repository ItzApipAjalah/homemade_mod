package website.amwp.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import com.mojang.brigadier.arguments.StringArgumentType;

public class ModsCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Main mods list command
        dispatcher.register(literal("mods")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    sendModList(player);
                }
                return 1;
            }));

        // Subcommand for mod details
        dispatcher.register(literal("modinfo")
            .then(argument("modid", StringArgumentType.word())
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                String modId = StringArgumentType.getString(context, "modid");
                if (player != null) {
                    sendModDetails(player, modId);
                }
                return 1;
            })));
    }

    private static void sendModList(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§6=== Server Mods ==="));
        
        Collection<ModContainer> mods = FabricLoader.getInstance().getAllMods();
        
        for (ModContainer container : mods) {
            String modId = container.getMetadata().getId();
            String modName = container.getMetadata().getName();
            String version = container.getMetadata().getVersion().getFriendlyString();
            
            // Create clickable mod name with hover text
            Text modText = Text.literal("§e• " + modName)
                .styled(style -> style
                    .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, 
                        "/modinfo " + modId
                    ))
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text.literal("§7Click to view details\n§8Version: " + version)
                    ))
                );
            
            player.sendMessage(modText);
        }
        
        int totalMods = mods.size();
        player.sendMessage(Text.literal("§6Total Mods: §e" + totalMods));
        player.sendMessage(Text.literal("§7Click on a mod name to view its details"));
    }

    private static void sendModDetails(ServerPlayerEntity player, String modId) {
        FabricLoader.getInstance().getModContainer(modId).ifPresentOrElse(
            container -> {
                String modName = container.getMetadata().getName();
                String version = container.getMetadata().getVersion().getFriendlyString();
                String description = container.getMetadata().getDescription();
                
                player.sendMessage(Text.literal("§6=== " + modName + " ==="));
                player.sendMessage(Text.literal("§7ID: §f" + modId));
                player.sendMessage(Text.literal("§7Version: §f" + version));
                
                if (!description.isEmpty()) {
                    player.sendMessage(Text.literal("§7Description: §f" + description));
                }
                
                // Add authors if available
                container.getMetadata().getAuthors().forEach(author -> {
                    player.sendMessage(Text.literal("§7Author: §f" + author.getName()));
                });
                
                // Add back button
                Text backButton = Text.literal("§7[Click to return to mod list]")
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/mods"
                        ))
                        .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Text.literal("§7Return to mod list")
                        ))
                    );
                player.sendMessage(backButton);
            },
            () -> player.sendMessage(Text.literal("§cMod not found!"))
        );
    }
} 