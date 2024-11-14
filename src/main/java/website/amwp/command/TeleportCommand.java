package website.amwp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TeleportCommand {
    // Store teleport requests: target player UUID -> requesting player UUID
    private static final Map<UUID, UUID> tpRequests = new HashMap<>();
    // Store teleport request types: target player UUID -> isHereRequest
    private static final Map<UUID, Boolean> tpRequestTypes = new HashMap<>();
    // Request expiry time in milliseconds (30 seconds)
    private static final long REQUEST_EXPIRY = 30000;
    // Store request timestamps
    private static final Map<UUID, Long> requestTimes = new HashMap<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /tpa <player> - Request to teleport to a player
        dispatcher.register(literal("tpa")
            .then(argument("player", StringArgumentType.word())
                .executes(context -> {
                    ServerPlayerEntity source = context.getSource().getPlayer();
                    String targetName = StringArgumentType.getString(context, "player");
                    ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);
                    
                    return handleTeleportRequest(source, target, false);
                })));

        // /tpahere <player> - Request a player to teleport to you
        dispatcher.register(literal("tpahere")
            .then(argument("player", StringArgumentType.word())
                .executes(context -> {
                    ServerPlayerEntity source = context.getSource().getPlayer();
                    String targetName = StringArgumentType.getString(context, "player");
                    ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);
                    
                    return handleTeleportRequest(source, target, true);
                })));

        // /tpaccept - Accept the pending teleport request
        dispatcher.register(literal("tpaccept")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                return handleTeleportAccept(player);
            }));

        // /tpadeny - Deny the pending teleport request
        dispatcher.register(literal("tpadeny")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                return handleTeleportDeny(player);
            }));
    }

    private static int handleTeleportRequest(ServerPlayerEntity source, ServerPlayerEntity target, boolean isHereRequest) {
        if (source == null) return 0;
        
        if (target == null) {
            source.sendMessage(Text.literal("§cPlayer not found!"));
            return 0;
        }

        if (source == target) {
            source.sendMessage(Text.literal("§cYou cannot teleport to yourself!"));
            return 0;
        }

        // Check if there's already a pending request
        if (tpRequests.containsKey(target.getUuid())) {
            long requestTime = requestTimes.get(target.getUuid());
            if (System.currentTimeMillis() - requestTime < REQUEST_EXPIRY) {
                source.sendMessage(Text.literal("§cThis player already has a pending teleport request!"));
                return 0;
            }
        }

        // Store the request
        tpRequests.put(target.getUuid(), source.getUuid());
        tpRequestTypes.put(target.getUuid(), isHereRequest);
        requestTimes.put(target.getUuid(), System.currentTimeMillis());

        // Send messages
        String requestMessage = isHereRequest 
            ? "§e" + source.getName().getString() + "§6 has requested you to teleport to them"
            : "§e" + source.getName().getString() + "§6 has requested to teleport to you";
        
        // Create clickable accept/deny buttons
        Text acceptButton = Text.literal("§a[Accept]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Text.literal("§7Click to accept teleport request"))));
                    
        Text denyButton = Text.literal("§c[Deny]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpadeny"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Text.literal("§7Click to deny teleport request"))));

        target.sendMessage(Text.literal(requestMessage));
        target.sendMessage(Text.literal("§6Click to respond: ").append(acceptButton).append(" ").append(denyButton));
        source.sendMessage(Text.literal("§6Teleport request sent to §e" + target.getName().getString()));

        // Schedule request expiry
        source.getServer().getOverworld().getServer().execute(() -> {
            if (tpRequests.containsKey(target.getUuid()) && 
                tpRequests.get(target.getUuid()).equals(source.getUuid())) {
                if (System.currentTimeMillis() - requestTimes.get(target.getUuid()) >= REQUEST_EXPIRY) {
                    tpRequests.remove(target.getUuid());
                    tpRequestTypes.remove(target.getUuid());
                    requestTimes.remove(target.getUuid());
                    source.sendMessage(Text.literal("§cTeleport request to §e" + target.getName().getString() + "§c has expired"));
                    target.sendMessage(Text.literal("§cTeleport request from §e" + source.getName().getString() + "§c has expired"));
                }
            }
        });

        return 1;
    }

    private static int handleTeleportAccept(ServerPlayerEntity player) {
        if (player == null) return 0;

        UUID targetUuid = player.getUuid();
        if (!tpRequests.containsKey(targetUuid)) {
            player.sendMessage(Text.literal("§cYou have no pending teleport requests!"));
            return 0;
        }

        // Get the requesting player
        ServerPlayerEntity requester = player.getServer().getPlayerManager().getPlayer(tpRequests.get(targetUuid));
        if (requester == null) {
            player.sendMessage(Text.literal("§cThe requesting player is no longer online!"));
            cleanupRequest(targetUuid);
            return 0;
        }

        // Check if the request has expired
        if (System.currentTimeMillis() - requestTimes.get(targetUuid) >= REQUEST_EXPIRY) {
            player.sendMessage(Text.literal("§cThe teleport request has expired!"));
            cleanupRequest(targetUuid);
            return 0;
        }

        // Perform the teleport based on request type
        boolean isHereRequest = tpRequestTypes.get(targetUuid);
        if (isHereRequest) {
            player.teleport(requester.getServerWorld(), 
                          requester.getX(), requester.getY(), requester.getZ(),
                          player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("§aTeleporting to §e" + requester.getName().getString()));
            requester.sendMessage(Text.literal("§e" + player.getName().getString() + "§a accepted your teleport request"));
        } else {
            requester.teleport(player.getServerWorld(),
                             player.getX(), player.getY(), player.getZ(),
                             requester.getYaw(), requester.getPitch());
            player.sendMessage(Text.literal("§e" + requester.getName().getString() + "§a is teleporting to you"));
            requester.sendMessage(Text.literal("§aTeleporting to §e" + player.getName().getString()));
        }

        // Clean up the request
        cleanupRequest(targetUuid);
        return 1;
    }

    private static int handleTeleportDeny(ServerPlayerEntity player) {
        if (player == null) return 0;

        UUID targetUuid = player.getUuid();
        if (!tpRequests.containsKey(targetUuid)) {
            player.sendMessage(Text.literal("§cYou have no pending teleport requests!"));
            return 0;
        }

        // Get the requesting player
        ServerPlayerEntity requester = player.getServer().getPlayerManager().getPlayer(tpRequests.get(targetUuid));
        
        // Send denial messages
        player.sendMessage(Text.literal("§cYou denied the teleport request"));
        if (requester != null) {
            requester.sendMessage(Text.literal("§e" + player.getName().getString() + "§c denied your teleport request"));
        }

        // Clean up the request
        cleanupRequest(targetUuid);
        return 1;
    }

    private static void cleanupRequest(UUID targetUuid) {
        tpRequests.remove(targetUuid);
        tpRequestTypes.remove(targetUuid);
        requestTimes.remove(targetUuid);
    }
} 