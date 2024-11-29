package website.amwp.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.minecraft.text.Text;
import website.amwp.ServerStats;
import website.amwp.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.dv8tion.jda.api.entities.Message;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.awt.Color;
import java.time.Instant;
import net.minecraft.util.Identifier;
import java.util.Optional;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.RichPresence;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.Permission;
import java.util.Arrays;
import net.minecraft.server.command.ServerCommandSource;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import website.amwp.discord.games.TicTacToe;
import net.dv8tion.jda.api.interactions.components.ActionRow;

public class DiscordBot extends ListenerAdapter {
    private static JDA jda;
    private static List<String> channelIds;
    private static boolean isInitialized = false;
    private static MinecraftServer server;
    private static final String STEVE_SKIN_URL = "https://minecraft.wiki/images/Steve_skin.png";
    private static final String SKIN_API_URL = "https://mc-heads.net/avatar/";
    private static final String WEBSITE_URL = "https://kizuserver.xyz";
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static long startTime = System.currentTimeMillis();
    private static final int PLAYERS_PER_PAGE = 10;
    private static final ConcurrentHashMap<String, PlayerListData> playerListCache = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService topicUpdater = Executors.newSingleThreadScheduledExecutor();
    private static StringBuilder backupMessageBuffer = new StringBuilder();
    private static boolean isBackupInProgress = false;
    
    // Add these constants for authorized users
    private static final List<String> AUTHORIZED_USERS = Arrays.asList(
        "481734993622728715",
        "403425777833738240"
    );

    private static final ConcurrentHashMap<String, TicTacToe> activeGames = new ConcurrentHashMap<>();

    private static class PlayerListData {
        final List<ServerPlayerEntity> players;
        final int maxPlayers;
        final long timestamp;
        
        PlayerListData(List<ServerPlayerEntity> players, int maxPlayers) {
            this.players = players;
            this.maxPlayers = maxPlayers;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 60000; // 1 minute expiry
        }
    }

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
        sendServerStartMessage();
    }

    public static void initialize() {
        if (isInitialized)
            return;

        ModConfig config = ModConfig.getInstance();
        String token = config.getDiscordToken();
        channelIds = config.getDiscordChannelIds();

        if (token.equals("your-token-here") || channelIds.isEmpty()) {
            ServerStats.LOGGER
                    .warn("Discord bot not configured! Please set token and channel IDs in config/server-stats.json");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordBot())
                    .build();

            jda.awaitReady();
            
            // Start topic updates
            startTopicUpdates();
            
            // Register all commands
            jda.updateCommands().addCommands(
                Commands.slash("playerlist", "Show online players"),
                Commands.slash("status", "Show server status"),
                Commands.slash("player", "Show player information")
                    .addOption(OptionType.STRING, "name", "Player name", true), 
                Commands.slash("ping", "Check bot latency"),
                Commands.slash("help", "Show all available commands"),
                Commands.slash("serverinfo", "Show server information"),
                Commands.slash("recipe", "Show item crafting recipe")
                    .addOption(OptionType.STRING, "item", "Item name (e.g. diamond_sword)", true),
                Commands.slash("backup", "Start a server backup"),
                Commands.slash("delete", "Delete bot messages")
                    .addOption(OptionType.INTEGER, "amount", "Number of messages to delete", true),
                Commands.slash("tictactoe", "Play Tic Tac Toe")
                    .addOption(OptionType.STRING, "difficulty", "Choose difficulty (easy/medium/hard)", true)
            ).queue();
            
            updateBotStatus();
            isInitialized = true;
            ServerStats.LOGGER.info("Discord bot successfully initialized!");
        } catch (Exception e) {
            ServerStats.LOGGER.error("Failed to initialize Discord bot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void broadcastToChannels(Consumer<TextChannel> messageAction) {
        if (!isInitialized || jda == null)
            return;

        List<String> currentChannelIds = ModConfig.getInstance().getDiscordChannelIds();

        for (String channelId : currentChannelIds) {
            try {
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null && channel.canTalk()) {
                    try {
                        messageAction.accept(channel);
                    } catch (Exception e) {
                        ServerStats.LOGGER.error("Failed to send message to channel " + channelId + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                ServerStats.LOGGER.error("Error accessing channel " + channelId + ": " + e.getMessage());
            }
        }
    }

    public static void sendServerStartMessage() {
        broadcastToChannels(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("Server is starting up!")
                    .addField("Version", server.getVersion(), true)
                    .setTimestamp(Instant.now())
                    .setFooter("Server Start", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue();
        });
    }

    public static void sendServerStopMessage() {
        broadcastToChannels(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Server is shutting down!")
                    .setTimestamp(Instant.now())
                    .setFooter("Server Stop", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue();
        });
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!channelIds.contains(event.getChannel().getId())) return;
        if (event.getAuthor().isBot()) return;

        String author = event.getAuthor().getName();
        
        // Handle attachments (images)
        if (!event.getMessage().getAttachments().isEmpty()) {
            for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                if (attachment.isImage()) {
                    String imageUrl = attachment.getUrl();
                    
                    // Create hoverable image message
                    Text imageText = Text.literal("§9[Discord] f" + author + ": ")
                        .append(Text.literal("§b[View Image]")
                            .styled(style -> style
                                .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("§7Click to view: " + imageUrl)
                                ))
                                .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.OPEN_URL,
                                    imageUrl
                                ))
                                .withColor(Formatting.AQUA)
                                .withUnderline(true)
                            ));
                    
                    if (server != null) {
                        server.getPlayerManager().broadcast(imageText, false);
                        ServerStats.LOGGER.info("Discord -> MC: " + author + " sent an image: " + imageUrl);
                    }
                }
            }
        }

        // Handle regular text message
        String message = event.getMessage().getContentDisplay();
        if (!message.isEmpty()) {
            if (server != null) {
                String formattedMessage = "§9[Discord] §f" + author + ": §7" + message;
                server.getPlayerManager().broadcast(Text.literal(formattedMessage), false);
                ServerStats.LOGGER.info("Discord -> MC: " + author + ": " + message);
            }
        }
    }

    public static void sendChatMessage(String playerName, String message) {
        broadcastToChannels(channel -> {
            channel.sendMessage("**" + playerName + "**: " + message)
                    .setSuppressedNotifications(true)
                    .queue();
        });
    }

    public static void sendPlayerJoinMessage(String playerName) {
        broadcastToChannels(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle(playerName + " joined the server")
                    .setThumbnail(SKIN_API_URL + playerName)
                    .setTimestamp(Instant.now())
                    .setFooter("Player Join", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue();
        });
    }

    public static void sendPlayerLeaveMessage(String playerName) {
        broadcastToChannels(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle(playerName + " left the server")
                    .setThumbnail(SKIN_API_URL + playerName)
                    .setTimestamp(Instant.now())
                    .setFooter("Player Leave", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue();
        });
    }

    private static void retryWithSteveSkin(TextChannel channel, String playerName, boolean isJoin) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(isJoin ? Color.GREEN : Color.RED)
                .setTitle(playerName + (isJoin ? " joined" : " left") + " the server")
                .setThumbnail(STEVE_SKIN_URL)
                .setTimestamp(Instant.now())
                .setFooter(isJoin ? "Player Join" : "Player Leave", null);

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> ServerStats.LOGGER
                        .info("Sent " + (isJoin ? "join" : "leave") + " message with Steve skin for " + playerName),
                error -> ServerStats.LOGGER
                        .error("Failed to send message even with Steve skin: " + error.getMessage()));
    }

    private static void retryWithSteveSkinDeath(TextChannel channel, String playerName, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(139, 0, 0))
                .setDescription(description)
                .setThumbnail(STEVE_SKIN_URL)
                .setTimestamp(Instant.now())
                .setFooter("Death Event", null);

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> ServerStats.LOGGER.info("Sent death message with Steve skin for " + playerName),
                error -> ServerStats.LOGGER
                        .error("Failed to send message even with Steve skin: " + error.getMessage()));
    }

    public static void shutdown() {
        if (isInitialized && jda != null) {
            // Shutdown topic updater
            topicUpdater.shutdown();
            try {
                topicUpdater.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ServerStats.LOGGER.error("Error shutting down topic updater: " + e.getMessage());
            }
            
            // Update topic to show server offline
            for (String channelId : channelIds) {
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null && channel.canTalk()) {
                    try {
                        if (channel.getGuild().getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                            channel.getManager().setTopic("🔴 Server Offline | 🌐 play.kizuserver.xyz").queue(
                                success -> {}, // Silent success
                                error -> {} // Silent error
                            );
                        }
                    } catch (Exception e) {
                        // Silently ignore permission errors
                    }
                }
            }

            // Existing shutdown code...
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ServerStats.LOGGER.error("Error shutting down scheduler: " + e.getMessage());
            }
            jda.shutdown();
            isInitialized = false;
        }
    }

    public static void sendPlayerDeathMessage(String playerName, Text deathMessage) {
        broadcastToChannels(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(139, 0, 0))
                    .setTitle("Player Death")
                    .setDescription(deathMessage.getString().replaceAll("§[0-9a-fk-or]", ""))
                    .setThumbnail(SKIN_API_URL + playerName)
                    .setTimestamp(Instant.now())
                    .setFooter("Death Event", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue();
        });
    }

    private static void retryWithSteveSkinAchievement(TextChannel channel, String playerName, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(255, 215, 0))
                .setTitle("Achievement Unlocked!")
                .setDescription(description)
                .setThumbnail(STEVE_SKIN_URL)
                .setTimestamp(Instant.now())
                .setFooter("Achievement Event", null);

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> ServerStats.LOGGER.info("Sent achievement message with Steve skin for " + playerName),
                error -> ServerStats.LOGGER.error("Failed to send message even with Steve skin: " + error.getMessage()));
    }

    public static void sendAchievementMessage(String playerName, Text achievementMessage) {
        broadcastToChannels(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(255, 215, 0))
                    .setTitle("Achievement Unlocked!")
                    .setDescription(achievementMessage.getString().replaceAll("§[0-9a-fk-or]", ""))
                    .setThumbnail(SKIN_API_URL + playerName)
                    .setTimestamp(Instant.now())
                    .setFooter("Achievement Event", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue();
        });
    }

    private static void updateBotStatus() {
        scheduler.scheduleAtFixedRate(() -> {
            if (jda != null) {
                String playtime = getPlaytime();
                Activity activity = Activity.playing("Minecraft | " + playtime + " | play.kizuserver.xyz");
                jda.getPresence().setActivity(activity);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private static String getPlaytime() {
        long uptime = System.currentTimeMillis() - startTime;
        long hours = TimeUnit.MILLISECONDS.toHours(uptime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "playerlist":
                handlePlayerList(event);
                break;
            case "status":
                handleStatus(event);
                break;
            case "player":
                handlePlayerInfo(event);
                break;
            case "ping":
                handlePing(event);
                break;
            case "help":
                handleHelp(event);
                break;
            case "serverinfo":
                handleServerInfo(event);
                break;
            case "recipe":
                handleRecipe(event);
                break;
            case "backup":
                handleBackup(event);
                break;
            case "delete":
                handleDelete(event);
                break;
            case "tictactoe":
                handleTicTacToe(event);
                break;
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        if (server == null) {
            event.reply("Server is not running!").setEphemeral(true).queue();
            return;
        }
    
        // Remove TPS calculation since it's not available
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
    
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(0, 170, 170))  // Changed from Color.BLUE
            .setTitle("Server Status")
            .addField("Memory", String.format("Memory: %d/%d MB", usedMemory, maxMemory), false)
            .addField("Players", server.getCurrentPlayerCount() + "/" + server.getMaxPlayerCount(), true)
            .addField("Version", server.getVersion(), true)
            .addField("Uptime", getPlaytime(), true)
            .setTimestamp(Instant.now());
    
        event.replyEmbeds(embed.build()).queue();
    }

    private void handlePlayerInfo(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("name").getAsString();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(player != null ? Color.GREEN : Color.RED)
            .setTitle("Player Information: " + playerName)
            .setThumbnail(SKIN_API_URL + playerName);

        if (player != null) {
            // Online player info
            embed.addField("Status", "Online ✅", true)
                .addField("Health", player.getHealth() + "❤", true)
                .addField("Level", player.experienceLevel + "⭐", true)
                .addField("Position", String.format("X: %d, Y: %d, Z: %d", 
                    (int)player.getX(), (int)player.getY(), (int)player.getZ()), false);
        } else {
            embed.setDescription("Player is currently offline ❌");
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private void handlePing(SlashCommandInteractionEvent event) {
        long time = System.currentTimeMillis();
        event.reply("Pinging...").queue(response -> {
            long ping = System.currentTimeMillis() - time;
            response.editOriginal("Pong! 🏓 Bot Latency: " + ping + "ms").queue();
        });
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("Available Commands")
            .setDescription("Here are all available commands:")
            .addField("/playerlist", "Show online players with stats", false)
            .addField("/status", "Show server status and performance", false)
            .addField("/player <name>", "Show detailed player information", false)
            .addField("/ping", "Check bot latency", false)
            .addField("/serverinfo", "Show server information", false)
            .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleServerInfo(SlashCommandInteractionEvent event) {
        if (server == null) {
            event.reply("Server is not running!").setEphemeral(true).queue();
            return;
        }
    
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(255, 215, 0))  // Changed from Color.GOLD to RGB values
            .setTitle("Server Information")
            .addField("Server Address", "play.kizuserver.xyz", true)
            .addField("Version", server.getVersion(), true)
            .addField("MOTD", ModConfig.getInstance().getFormattedMotd().replaceAll("§[0-9a-fk-or]", ""), false)
            .addField("Players", server.getCurrentPlayerCount() + "/" + server.getMaxPlayerCount() + " players online", true)
            .addField("Uptime", getPlaytime(), true)
            .setTimestamp(Instant.now());
    
        event.replyEmbeds(embed.build()).queue();
    }

    private void handlePlayerList(SlashCommandInteractionEvent event) {
        if (server == null) {
            event.reply("Server is not running!").setEphemeral(true).queue();
            return;
        }

        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        int maxPlayers = server.getMaxPlayerCount();
        
        // Create a unique ID for this player list
        String listId = UUID.randomUUID().toString();
        playerListCache.put(listId, new PlayerListData(players, maxPlayers));
        
        // Send the first page
        sendPlayerListPage(event, listId, 0);
    }

    private void sendPlayerListPage(SlashCommandInteractionEvent event, String listId, int page) {
        PlayerListData data = playerListCache.get(listId);
        if (data == null || data.isExpired()) {
            event.reply("Player list data expired. Please use /playerlist again.").setEphemeral(true).queue();
            return;
        }

        List<ServerPlayerEntity> players = data.players;
        int maxPlayers = data.maxPlayers;
        int totalPages = (players.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE;
        
        if (players.isEmpty()) {
            event.replyEmbeds(new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("Player List")
                .setDescription("No players online")
                .setFooter("0/" + maxPlayers + " players", null)
                .setTimestamp(Instant.now())
                .build()).queue();
            return;
        }

        // Calculate page bounds
        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, players.size());
        List<ServerPlayerEntity> pagePlayers = players.subList(startIndex, endIndex);

        // Build player list with heads
        StringBuilder description = new StringBuilder();
        for (ServerPlayerEntity player : pagePlayers) {
            String playerName = player.getName().getString();
            description.append("[")
                      .append(playerName)
                      .append("](")
                      .append(SKIN_API_URL)
                      .append(playerName)
                      .append(") - ")
                      .append(player.getHealth())
                      .append("❤ ")
                      .append(player.experienceLevel)
                      .append("⭐\n");
        }

        // Create embed
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("Player List")
            .setDescription(description.toString())
            .setFooter(String.format("Page %d/%d • %d/%d players", 
                page + 1, Math.max(1, totalPages), players.size(), maxPlayers), null)
            .setTimestamp(Instant.now());

        // Create buttons
        List<Button> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(Button.primary("playerlist:" + listId + ":prev", "◀️ Previous"));
        }
        if (endIndex < players.size()) {
            buttons.add(Button.primary("playerlist:" + listId + ":next", "Next ▶️"));
        }
        buttons.add(Button.secondary("playerlist:" + listId + ":refresh", "🔄 Refresh"));

        // Send the message
        if (event.isAcknowledged()) {
            event.getHook().editOriginalEmbeds(embed.build())
                .setActionRow(buttons)
                .queue();
        } else {
            event.replyEmbeds(embed.build())
                .addActionRow(buttons)
                .queue();
        }
    }

    private void handlePlayerListUpdate(ButtonInteractionEvent event, String listId, int page) {
        PlayerListData data = playerListCache.get(listId);
        if (data == null || data.isExpired()) {
            event.reply("Player list data expired. Please use /playerlist again.").setEphemeral(true).queue();
            return;
        }

        List<ServerPlayerEntity> players = data.players;
        int maxPlayers = data.maxPlayers;
        int totalPages = (players.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE;
        
        if (players.isEmpty()) {
            event.editMessageEmbeds(new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("Player List")
                .setDescription("No players online")
                .setFooter("0/" + maxPlayers + " players", null)
                .setTimestamp(Instant.now())
                .build()).queue();
            return;
        }

        // Calculate page bounds
        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, players.size());
        List<ServerPlayerEntity> pagePlayers = players.subList(startIndex, endIndex);

        // Build player list with heads
        StringBuilder description = new StringBuilder();
        for (ServerPlayerEntity player : pagePlayers) {
            String playerName = player.getName().getString();
            description.append("[")
                      .append(playerName)
                      .append("](")
                      .append(SKIN_API_URL)
                      .append(playerName)
                      .append(") - ")
                      .append(player.getHealth())
                      .append("❤ ")
                      .append(player.experienceLevel)
                      .append("⭐\n");
        }

        // Create embed
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("Player List")
            .setDescription(description.toString())
            .setFooter(String.format("Page %d/%d • %d/%d players", 
                page + 1, Math.max(1, totalPages), players.size(), maxPlayers), null)
            .setTimestamp(Instant.now());

        // Create buttons
        List<Button> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(Button.primary("playerlist:" + listId + ":prev", "◀️ Previous"));
        }
        if (endIndex < players.size()) {
            buttons.add(Button.primary("playerlist:" + listId + ":next", "Next ▶️"));
        }
        buttons.add(Button.secondary("playerlist:" + listId + ":refresh", "🔄 Refresh"));

        // Update the message
        event.editMessageEmbeds(embed.build())
            .setActionRow(buttons)
            .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] id = event.getComponentId().split(",");
        if (id.length == 2) {
            try {
                int row = Integer.parseInt(id[0]);
                int col = Integer.parseInt(id[1]);
                
                TicTacToe game = activeGames.get(event.getUser().getId());
                if (game != null) {
                    EmbedBuilder response = game.makeMove(row, col, event.getUser());
                    
                    // Create action rows for buttons
                    List<Button> buttons = game.getButtons();
                    List<ActionRow> actionRows = Arrays.asList(
                        ActionRow.of(buttons.subList(0, 3)),
                        ActionRow.of(buttons.subList(3, 6)),
                        ActionRow.of(buttons.subList(6, 9))
                    );
                    
                    event.editMessageEmbeds(response.build())
                        .setComponents(actionRows)
                        .queue();

                    // Remove finished games
                    if (response.build().getFooter().getText().contains("Game Over")) {
                        activeGames.remove(event.getUser().getId());
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore invalid button IDs
            }
        }
    }

    private void handleRecipe(SlashCommandInteractionEvent event) {
        String itemName = event.getOption("item").getAsString().toLowerCase()
            .replace(" ", "_")
            .replace("minecraft:", "");
        
        // Base URL for recipe images
        String recipeUrl = "https://minecraft.wiki/images/Recipe_" + itemName + ".png";
        
        // Alternative URLs in case the first one doesn't work
        String craftingUrl = "https://www.minecraft-crafting.net/app/src/items/" + itemName.replace("_", "-") + "/how-to-craft.png";

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(139, 69, 19)) // Brown color
            .setTitle("Crafting Recipe: " + itemName.replace("_", " "))
            .setDescription("Here's how to craft " + itemName.replace("_", " "))
            .setImage(recipeUrl)
            .addField("Alternative Links", 
                "[View on Minecraft Wiki](https://minecraft.wiki/w/" + itemName.replace("_", "_") + "#Crafting)\n" +
                "[View on Crafting Guide](https://www.minecraft-crafting.net/?i=" + itemName.replace("_", "-") + ")", 
                false)
            .setTimestamp(Instant.now())
            .setFooter("Recipe Guide", null);

        event.replyEmbeds(embed.build()).queue(
            success -> ServerStats.LOGGER.info("Sent recipe for " + itemName),
            error -> {
                // If first image fails, try alternative
                embed.setImage(craftingUrl);
                event.getHook().editOriginalEmbeds(embed.build()).queue(
                    success2 -> ServerStats.LOGGER.info("Sent alternative recipe for " + itemName),
                    error2 -> event.getHook().editOriginal("Couldn't find recipe for: " + itemName).queue()
                );
            }
        );
    }

    private static final long TOPIC_UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minutes
    private static long lastTopicUpdate = 0;
    private static boolean isRateLimited = false;
    private static long rateLimitResetTime = 0;

    private static void startTopicUpdates() {
        topicUpdater.scheduleAtFixedRate(() -> {
            if (server != null && jda != null) {
                long currentTime = System.currentTimeMillis();
                
                // Check if we're rate limited
                if (isRateLimited && currentTime < rateLimitResetTime) {
                    return;
                }
                
                // Check if enough time has passed since last update
                if (currentTime - lastTopicUpdate < TOPIC_UPDATE_INTERVAL) {
                    return;
                }

                isRateLimited = false;
                String status = formatServerStatus();
                
                // Update topic for all configured channels
                for (String channelId : channelIds) {
                    TextChannel channel = jda.getTextChannelById(channelId);
                    if (channel != null && channel.canTalk()) {
                        try {
                            if (channel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) {
                                channel.getManager().setTopic(status)
                                    .queue(
                                        success -> {
                                            lastTopicUpdate = currentTime;
                                        },
                                        error -> {
                                            if (error instanceof RateLimitedException) {
                                                RateLimitedException rle = (RateLimitedException) error;
                                                isRateLimited = true;
                                                rateLimitResetTime = currentTime + rle.getRetryAfter();
                                            }
                                        }
                                    );
                            }
                        } catch (Exception e) {
                            // Silently ignore permission errors
                        }
                    }
                }
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private static String formatServerStatus() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        int playerCount = server.getCurrentPlayerCount();
        int maxPlayers = server.getMaxPlayerCount();
        
        return String.format("🟢 Server Online | 👥 %d/%d Players | ⏰ Uptime: %s | 💾 Memory: %d/%d MB | 🌐 play.kizuserver.xyz",
            playerCount, maxPlayers, getPlaytime(), usedMemory, maxMemory);
    }

    public static void handleBackupMessage(String message) {
        if (message.contains("Starting backup process")) {
            isBackupInProgress = true;
            backupMessageBuffer = new StringBuilder();
            sendBackupStartMessage();
        }
        
        if (isBackupInProgress) {
            backupMessageBuffer.append(message.replace("[Backup] ", "")).append("\n");
            
            if (message.contains("Backup completed successfully")) {
                isBackupInProgress = false;
                sendBackupCompleteMessage();
            }
        }
    }

    private static void sendBackupStartMessage() {
        broadcastToChannels(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(255, 165, 0))
                    .setTitle("🔄 Backup Started")
                    .setDescription("Server backup process has started. You might experience slight lag.")
                    .setTimestamp(Instant.now())
                    .setFooter("Backup System", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue(message -> {
                        // Delete message after 1 minute
                        message.delete().queueAfter(1, TimeUnit.MINUTES, 
                            success -> {}, 
                            error -> {}  // Ignore deletion errors
                        );
                    });
        });
    }

    private static void sendBackupCompleteMessage() {
        broadcastToChannels(channel -> {
            String details = backupMessageBuffer.toString()
                    .replaceAll("(?m)^Creating zip file.*$", "")
                    .replaceAll("(?m)^Uploading.*$", "")
                    .trim();

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0, 255, 0))
                    .setTitle("✅ Backup Completed")
                    .setDescription("Server backup has completed successfully!")
                    .addField("Backup Details", "```" + details + "```", false)
                    .setTimestamp(Instant.now())
                    .setFooter("Backup System", null);

            channel.sendMessageEmbeds(embed.build())
                    .setSuppressedNotifications(true)
                    .queue(message -> {
                        // Delete message after 1 minute
                        message.delete().queueAfter(1, TimeUnit.MINUTES, 
                            success -> {}, 
                            error -> {}  // Ignore deletion errors
                        );
                    });
        });
    }

    private void handleBackup(SlashCommandInteractionEvent event) {
        // Check if user is authorized
        if (!AUTHORIZED_USERS.contains(event.getUser().getId())) {
            event.reply("❌ You are not authorized to use this command!")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (server == null) {
            event.reply("Server is not running!").setEphemeral(true).queue();
            return;
        }

        // Acknowledge the command immediately
        event.deferReply().queue();

        try {
            // Execute backup command using the server's command dispatcher
            server.getCommandManager().executeWithPrefix(
                server.getCommandSource(),
                "drivebackup backup"
            );

            // Send initial response
            event.getHook().editOriginal("✅ Backup command executed. Check status in backup messages.").queue();
        } catch (Exception e) {
            event.getHook().editOriginal("❌ Failed to execute backup command: " + e.getMessage()).queue();
            ServerStats.LOGGER.error("Failed to execute backup command", e);
        }
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        // Check if user is authorized
        if (!AUTHORIZED_USERS.contains(event.getUser().getId())) {
            event.reply("❌ You are not authorized to use this command!")
                .setEphemeral(true)
                .queue();
            return;
        }

        // Get amount parameter
        int amount = event.getOption("amount").getAsInt();
        if (amount < 1 || amount > 100) {
            event.reply("❌ Please specify a number between 1 and 100")
                .setEphemeral(true)
                .queue();
            return;
        }

        // Acknowledge the command immediately
        event.deferReply().setEphemeral(true).queue();

        // Get channel and bot ID
        TextChannel channel = event.getChannel().asTextChannel();
        String botId = event.getJDA().getSelfUser().getId();

        // Fetch messages
        channel.getHistory().retrievePast(100).queue(messages -> {
            // Filter bot messages and limit to requested amount
            List<Message> botMessages = messages.stream()
                .filter(msg -> msg.getAuthor().getId().equals(botId))
                .limit(amount)
                .collect(Collectors.toList());

            if (botMessages.isEmpty()) {
                event.getHook().editOriginal("❌ No bot messages found to delete.").queue();
                return;
            }

            // Delete messages
            if (botMessages.size() == 1) {
                botMessages.get(0).delete().queue(
                    success -> event.getHook().editOriginal("✅ Deleted 1 message").queue(),
                    error -> event.getHook().editOriginal("❌ Failed to delete message: " + error.getMessage()).queue()
                );
            } else {
                channel.deleteMessages(botMessages).queue(
                    success -> event.getHook().editOriginal("✅ Deleted " + botMessages.size() + " messages").queue(),
                    error -> event.getHook().editOriginal("❌ Failed to delete messages: " + error.getMessage()).queue()
                );
            }
        });
    }

    private void handleTicTacToe(SlashCommandInteractionEvent event) {
        String difficulty = event.getOption("difficulty").getAsString().toLowerCase();
        if (!Arrays.asList("easy", "medium", "hard").contains(difficulty)) {
            event.reply("Please choose a valid difficulty: easy, medium, or hard").setEphemeral(true).queue();
            return;
        }

        TicTacToe game = new TicTacToe(event.getUser(), difficulty);
        activeGames.put(event.getUser().getId(), game);
        
        event.replyEmbeds(game.getGameBoard("Your turn!").build())
            .addActionRow(game.getButtons().subList(0, 3))
            .addActionRow(game.getButtons().subList(3, 6))
            .addActionRow(game.getButtons().subList(6, 9))
            .queue();
    }
}