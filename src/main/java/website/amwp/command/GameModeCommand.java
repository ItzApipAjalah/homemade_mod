package website.amwp.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class GameModeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Creative Mode Command
        dispatcher.register(literal("gmc")
            .requires(source -> source.hasPermissionLevel(2)) // Requires operator permission level 2
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    player.changeGameMode(GameMode.CREATIVE);
                    player.sendMessage(Text.literal("§aGamemode set to Creative"));
                }
                return 1;
            }));

        // Survival Mode Command
        dispatcher.register(literal("gms")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    player.changeGameMode(GameMode.SURVIVAL);
                    player.sendMessage(Text.literal("§aGamemode set to Survival"));
                }
                return 1;
            }));

        // Spectator Mode Command
        dispatcher.register(literal("gmsp")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    player.changeGameMode(GameMode.SPECTATOR);
                    player.sendMessage(Text.literal("§aGamemode set to Spectator"));
                }
                return 1;
            }));

        // Adventure Mode Command
        dispatcher.register(literal("gma")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    player.changeGameMode(GameMode.ADVENTURE);
                    player.sendMessage(Text.literal("§aGamemode set to Adventure"));
                }
                return 1;
            }));
    }
} 