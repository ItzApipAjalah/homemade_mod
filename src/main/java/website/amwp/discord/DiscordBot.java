package website.amwp.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.text.Text;
import website.amwp.ServerStats;
import website.amwp.config.ModConfig;
import net.minecraft.server.MinecraftServer;

public class DiscordBot extends ListenerAdapter {
    private static JDA jda;
    private static String channelId;
    private static boolean isInitialized = false;
    private static MinecraftServer server;

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
        sendServerStartMessage();
    }

    public static void initialize() {
        if (isInitialized)
            return;

        ModConfig config = ModConfig.getInstance();
        String token = config.getDiscordToken();
        channelId = config.getDiscordChannelId();

        if (token.equals("your-token-here") || channelId.equals("your-channel-id-here")) {
            ServerStats.LOGGER
                    .warn("Discord bot not configured! Please set token and channel ID in config/server-stats.json");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordBot())
                    .build()
                    .awaitReady();
            isInitialized = true;
            ServerStats.LOGGER.info("Discord bot successfully initialized!");
        } catch (Exception e) {
            ServerStats.LOGGER.error("Failed to initialize Discord bot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendServerStartMessage() {
        if (!isInitialized || jda == null)
            return;

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage("🟢 **Server is starting up!**\n" +
                        "```\n" +
                        "Server version: " + server.getVersion() + "\n" +
                        "```").queue(
                                success -> ServerStats.LOGGER.info("Sent server start message to Discord"),
                                error -> ServerStats.LOGGER
                                        .error("Failed to send server start message: " + error.getMessage()));
            }
        } catch (Exception e) {
            ServerStats.LOGGER.error("Error sending server start message: " + e.getMessage());
        }
    }

    public static void sendServerStopMessage() {
        if (!isInitialized || jda == null)
            return;

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage("🔴 **Server is shutting down!**").queue(
                        success -> {
                            ServerStats.LOGGER.info("Sent server stop message to Discord");
                            shutdown();
                        },
                        error -> ServerStats.LOGGER.error("Failed to send server stop message: " + error.getMessage()));
            }
        } catch (Exception e) {
            ServerStats.LOGGER.error("Error sending server stop message: " + e.getMessage());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getChannel().getId().equals(channelId))
            return;
        if (event.getAuthor().isBot())
            return;

        String message = event.getMessage().getContentDisplay();
        String author = event.getAuthor().getName();

        // Forward Discord message to Minecraft
        if (server != null && !message.isEmpty()) {
            String formattedMessage = "§9[Discord] §f" + author + ": §7" + message;
            server.getPlayerManager().broadcast(Text.literal(formattedMessage), false);
            ServerStats.LOGGER.info("Discord -> MC: " + author + ": " + message);
        }
    }

    public static void sendChatMessage(String playerName, String message) {
        if (!isInitialized || jda == null)
            return;

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage("**" + playerName + "**: " + message).queue(
                        success -> ServerStats.LOGGER.info("MC -> Discord: " + playerName + ": " + message),
                        error -> ServerStats.LOGGER
                                .error("Failed to send chat message to Discord: " + error.getMessage()));
            } else {
                ServerStats.LOGGER.error("Could not find Discord channel with ID: " + channelId);
            }
        } catch (Exception e) {
            ServerStats.LOGGER.error("Error sending chat message to Discord: " + e.getMessage());
        }
    }

    public static void sendPlayerJoinMessage(String playerName) {
        if (!isInitialized || jda == null)
            return;

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage("🟢 **" + playerName + "** joined the server").queue(
                        success -> ServerStats.LOGGER.info("Sent join message to Discord for " + playerName),
                        error -> ServerStats.LOGGER
                                .error("Failed to send join message to Discord: " + error.getMessage()));
            }
        } catch (Exception e) {
            ServerStats.LOGGER.error("Error sending join message to Discord: " + e.getMessage());
        }
    }

    public static void sendPlayerLeaveMessage(String playerName) {
        if (!isInitialized || jda == null)
            return;

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage("🔴 **" + playerName + "** left the server").queue(
                        success -> ServerStats.LOGGER.info("Sent leave message to Discord for " + playerName),
                        error -> ServerStats.LOGGER
                                .error("Failed to send leave message to Discord: " + error.getMessage()));
            }
        } catch (Exception e) {
            ServerStats.LOGGER.error("Error sending leave message to Discord: " + e.getMessage());
        }
    }

    public static void shutdown() {
        if (isInitialized && jda != null) {
            jda.shutdown();
            isInitialized = false;
        }
    }
}