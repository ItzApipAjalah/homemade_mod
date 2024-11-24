package website.amwp.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import website.amwp.ApiService;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class UpdateModpackCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("upmodpack")
                .requires(source -> source.hasPermissionLevel(4)) // Operator only (permission level 4)
                .then(argument("url", StringArgumentType.greedyString())
                        .executes(context -> {
                            String url = StringArgumentType.getString(context, "url");
                            ServerCommandSource source = context.getSource();
                            
                            ApiService.updateModpack(url);
                            source.sendMessage(Text.literal("§aModpack update initiated with URL: " + url));
                            
                            return Command.SINGLE_SUCCESS;
                        })));
    }
} 