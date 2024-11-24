package website.amwp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import website.amwp.config.ModConfig;
import website.amwp.discord.DiscordBot;
import website.amwp.command.GameModeCommand;
import website.amwp.command.ModsCommand;
import website.amwp.command.TeleportCommand;
import website.amwp.command.AdminCommand;
import website.amwp.tab.TabManager;
import website.amwp.commands.UpdateModpackCommand;
import website.amwp.config.NameTagManager;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import net.minecraft.server.command.ServerCommandSource;

public class ServerStats implements ModInitializer {
	public static final String MOD_ID = "statsmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static int tickCounter = 0;
	private static MinecraftServer server;
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	@Override
	public void onInitialize() {
		// Delete folder at the very start
		NameTagManager.deleteNameTagFolder();

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			NameTagManager.deleteNameTagFolder();
		});		

		// Create config when server fully starts
		ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
			server = minecraftServer;
			NameTagManager.createNameTagConfig();
			
			// Schedule nametag reload command with a slight delay to ensure config is loaded
			scheduler.schedule(() -> {
				try {
					ServerCommandSource source = server.getCommandSource();
					server.getCommandManager().executeWithPrefix(source, "nametag reload");
					LOGGER.info("Nametag config reloaded successfully");
				} catch (Exception e) {
					LOGGER.error("Failed to reload nametag config: " + e.getMessage());
				}
			}, 2, TimeUnit.SECONDS);
			
			// Rest of your server started code...
		});

		// Listen for server start errors
		ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> {
			Thread errorWatcher = new Thread(() -> {
				try {
					// Wait a moment for logs to be written
					Thread.sleep(1000);
					
					// Check logs for error message
					if (isErrorInLogs()) {
						LOGGER.error("Detected server start failure. Shutting down in 3 seconds...");
						
						// Wait 3 seconds
						Thread.sleep(3000);
						
						// Stop the server
						if (server != null) {
							server.stop(false);
						} else {
							Runtime.getRuntime().halt(1);
						}
					}
				} catch (InterruptedException e) {
					LOGGER.error("Error in error watcher thread: " + e.getMessage());
				}
			});
			errorWatcher.setDaemon(true);
			errorWatcher.start();
		});

		// Load config and initialize Discord bot
		ModConfig.getInstance();
		DiscordBot.initialize();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			GameModeCommand.register(dispatcher);
			ModsCommand.register(dispatcher);
			TeleportCommand.register(dispatcher);
			AdminCommand.register(dispatcher);
			UpdateModpackCommand.register(dispatcher);
		});

		// Server starting event - Initialize MOTD and tab
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			LOGGER.info("Initializing server MOTD and tab...");
			
			// Set MOTD
			String motd = ModConfig.getInstance().getFormattedMotd();
			server.setMotd(motd);
			LOGGER.info("MOTD set to: " + motd);
			
			// Set Discord bot server instance
			DiscordBot.setServer(server);
		});

		// Server started event - Initialize tab updates
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("Server started, initializing tab updates...");
			// Initial tab update
			TabManager.updateAllPlayers(server);
		});

		// Register server shutdown event
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DiscordBot.sendServerStopMessage();
		});

		// Player join event
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			String playerName = handler.getPlayer().getName().getString();
			ApiService.createPlayer(playerName);
			ApiService.playerJoinServer(playerName);
			DiscordBot.sendPlayerJoinMessage(playerName);
			// Update tab for the joining player
			TabManager.updatePlayerTab(handler.getPlayer());
			// Update tab for all players to show new player count
			TabManager.updateAllPlayers(server);
		});

		// Player leave event
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			String playerName = handler.getPlayer().getName().getString();
			ApiService.playerLeaveServer(playerName);
			DiscordBot.sendPlayerLeaveMessage(playerName);
			// Update tab for all remaining players
			TabManager.updateAllPlayers(server);
		});

		// Register server tick event for tab updates
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			// Update tab every 100 ticks (5 seconds)
			if (tickCounter >= 100) {
				TabManager.updateAllPlayers(server);
				tickCounter = 0;
			}
		});

		LOGGER.info("ServerStats mod initialized!");
	}

	private boolean isErrorInLogs() {
		try {
			Path logFile = Paths.get("logs", "latest.log");
			if (Files.exists(logFile)) {
				List<String> lines = Files.readAllLines(logFile);
				// Check last few lines for the error message
				for (int i = Math.max(0, lines.size() - 50); i < lines.size(); i++) {
					if (lines.get(i).contains("Failed to start the minecraft server")) {
						return true;
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("Error reading log file: " + e.getMessage());
		}
		return false;
	}

	// Add shutdown hook to clean up resources
	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				scheduler.shutdownNow();
				try {
					scheduler.awaitTermination(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					LOGGER.error("Error shutting down scheduler: " + e.getMessage());
				}
			}));
	}
}