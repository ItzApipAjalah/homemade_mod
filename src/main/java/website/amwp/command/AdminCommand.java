package website.amwp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import website.amwp.config.ModConfig;
import website.amwp.tab.TabManager;

import java.util.Arrays;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class AdminCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /areload command
        dispatcher.register(literal("areload")
            .requires(source -> source.hasPermissionLevel(4)) // Requires operator level 4
            .executes(context -> {
                ModConfig.reloadConfig();
                String currentMotd = ModConfig.getInstance().getFormattedMotd();
                context.getSource().getServer().setMotd(currentMotd);
                context.getSource().sendMessage(Text.literal("§aConfig reloaded successfully!"));
                return 1;
            }));

        // /setmotd command
        dispatcher.register(literal("setmotd")
            .requires(source -> source.hasPermissionLevel(4))
            .then(argument("line", StringArgumentType.greedyString())
                .executes(context -> {
                    String motdInput = StringArgumentType.getString(context, "line");
                    List<String> motdLines = Arrays.asList(motdInput.split("\\\\n")); // Use \n for new line
                    
                    ModConfig.getInstance().setServerMotd(motdLines);
                    context.getSource().getServer().setMotd(ModConfig.getInstance().getFormattedMotd());
                    
                    // Show preview
                    context.getSource().sendMessage(Text.literal("§aServer MOTD updated to:"));
                    for (String line : motdLines) {
                        context.getSource().sendMessage(Text.literal("§f" + line));
                    }
                    return 1;
                })));

        // /motd command to view current MOTD
        dispatcher.register(literal("motd")
            .requires(source -> source.hasPermissionLevel(4))
            .executes(context -> {
                List<String> currentMotd = ModConfig.getInstance().getServerMotd();
                context.getSource().sendMessage(Text.literal("§6Current MOTD:"));
                for (String line : currentMotd) {
                    context.getSource().sendMessage(Text.literal("§f" + line));
                }
                return 1;
            }));

        // Tab header command
        dispatcher.register(literal("settabheader")
            .requires(source -> source.hasPermissionLevel(4))
            .then(argument("header", StringArgumentType.greedyString())
                .executes(context -> {
                    String headerInput = StringArgumentType.getString(context, "header");
                    List<String> headerLines = Arrays.asList(headerInput.split("\\\\n"));
                    
                    ModConfig.getInstance().setTabHeader(headerLines);
                    TabManager.updateAllPlayers(context.getSource().getServer());
                    
                    context.getSource().sendMessage(Text.literal("§aTab header updated!"));
                    return 1;
                })));

        // Tab footer command
        dispatcher.register(literal("settabfooter")
            .requires(source -> source.hasPermissionLevel(4))
            .then(argument("footer", StringArgumentType.greedyString())
                .executes(context -> {
                    String footerInput = StringArgumentType.getString(context, "footer");
                    List<String> footerLines = Arrays.asList(footerInput.split("\\\\n"));
                    
                    ModConfig.getInstance().setTabFooter(footerLines);
                    TabManager.updateAllPlayers(context.getSource().getServer());
                    
                    context.getSource().sendMessage(Text.literal("§aTab footer updated!"));
                    return 1;
                })));
    }
} 